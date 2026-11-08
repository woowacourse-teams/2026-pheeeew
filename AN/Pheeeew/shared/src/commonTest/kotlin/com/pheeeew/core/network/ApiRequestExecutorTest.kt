package com.pheeeew.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiRequestExecutorTest {
    @Test
    fun `인증 요청에 공급된 bearer token을 붙인다`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                        respond("""{"ok":true}""", headers = jsonHeaders())
                    },
                    AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val result = client.requests.execute(readRequest()) { it.body<PingResponse>() }

                assertEquals(ApiResult.Success(PingResponse(ok = true)), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `인증 없는 요청은 token provider 없이 실행한다`() =
        runTest {
            val client =
                createClient(
                    MockEngine { request ->
                        assertEquals(null, request.headers[HttpHeaders.Authorization])
                        respond("""{"ok":true}""", headers = jsonHeaders())
                    },
                    tokenProvider = null,
                )

            try {
                val result =
                    client.requests.execute(
                        readRequest(authentication = AuthenticationRequirement.NONE),
                    ) { it.body<PingResponse>() }

                assertEquals(ApiResult.Success(PingResponse(ok = true)), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `인증 token이 없으면 통신 전에 명시적으로 실패한다`() =
        runTest {
            var requestCount = 0
            val client =
                createClient(
                    MockEngine {
                        requestCount++
                        respond("{}", headers = jsonHeaders())
                    },
                    AccessTokenProvider { null },
                )

            try {
                val result = client.requests.execute(readRequest()) { it.body<PingResponse>() }

                val failure = assertIs<ApiResult.Failure>(result).reason
                assertIs<NetworkFailure.SessionUnavailable>(failure)
                assertEquals(0, requestCount)
            } finally {
                client.close()
            }
        }

    @Test
    fun `204 응답은 body decode 없이 성공한다`() =
        runTest {
            val client = createClient(MockEngine { respond("", status = HttpStatusCode.NoContent) })

            try {
                val result =
                    client.requests.executeNoContent(
                        ApiRequest(HttpMethod.Delete, "/test-items/1", RequestKind.WRITE),
                    )

                assertEquals(ApiResult.Success(Unit), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `JSON error와 Retry-After를 HTTP failure로 보존한다`() =
        runTest {
            val client =
                createClient(
                    MockEngine {
                        respond(
                            content = """{"code":"RATE_LIMITED","message":"slow down"}""",
                            status = HttpStatusCode.TooManyRequests,
                            headers =
                                headersOf(
                                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                    HttpHeaders.RetryAfter to listOf("3"),
                                ),
                        )
                    },
                )

            try {
                val result = client.requests.execute(readRequest()) { it.body<PingResponse>() }

                val failure = assertIs<ApiResult.Failure>(result).reason
                assertEquals(
                    NetworkFailure.HttpStatus(
                        statusCode = 429,
                        error = ApiError("RATE_LIMITED", "slow down"),
                        retryAfter = "3",
                        mutationCertainty = MutationCertainty.NOT_A_MUTATION,
                    ),
                    failure,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `401과 403을 HTTP status로 구분한다`() =
        runTest {
            listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
                val client = createClient(MockEngine { respond("", status = status) })

                try {
                    val result = client.requests.execute(readRequest()) { it.body<PingResponse>() }

                    val failure = assertIs<ApiResult.Failure>(result).reason
                    assertEquals(status.value, assertIs<NetworkFailure.HttpStatus>(failure).statusCode)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `JSON이 아닌 서버 오류도 HTTP status로 보존한다`() =
        runTest {
            val client =
                createClient(
                    MockEngine { respond("upstream unavailable", status = HttpStatusCode.BadGateway) },
                )

            try {
                val result =
                    client.requests.execute(
                        ApiRequest(HttpMethod.Post, "/test-items", RequestKind.WRITE),
                    ) { it.body<PingResponse>() }

                val failure = assertIs<NetworkFailure.HttpStatus>(assertIs<ApiResult.Failure>(result).reason)
                assertEquals(502, failure.statusCode)
                assertEquals(null, failure.error)
                assertEquals(MutationCertainty.UNKNOWN, failure.mutationCertainty)
            } finally {
                client.close()
            }
        }

    @Test
    fun `성공 응답 body 계약 오류를 구분한다`() =
        runTest {
            val client = createClient(MockEngine { respond("{}", headers = jsonHeaders()) })

            try {
                val result = client.requests.execute(readRequest()) { it.body<PingResponse>() }

                val failure = assertIs<ApiResult.Failure>(result).reason
                assertEquals(
                    NetworkFailure.Contract(
                        reason = ContractFailureReason.MALFORMED_SUCCESS_BODY,
                        mutationCertainty = MutationCertainty.NOT_A_MUTATION,
                    ),
                    failure,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `취소는 API failure로 바꾸지 않고 전파한다`() =
        runTest {
            val client = createClient(MockEngine { throw CancellationException("cancelled") })

            try {
                assertFailsWith<CancellationException> {
                    client.requests.execute(readRequest()) { it.body<PingResponse>() }
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun `request timeout은 transport failure로 구분한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            delay(100)
                            respond("{}", headers = jsonHeaders())
                        },
                    config =
                        ApiConfig(
                            baseUrl = "https://api.example.test",
                            timeouts = NetworkTimeouts(requestMillis = 1, connectMillis = 1_000, socketMillis = 1_000),
                        ),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val result =
                    client.requests.execute(
                        ApiRequest(HttpMethod.Post, "/test-items", RequestKind.WRITE),
                    ) { it.body<PingResponse>() }

                assertEquals(
                    ApiResult.Failure(
                        NetworkFailure.Transport(
                            reason = TransportFailureReason.TIMEOUT,
                            mutationCertainty = MutationCertainty.UNKNOWN,
                        ),
                    ),
                    result,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `stream endpoint는 Accept와 개별 timeout 설정을 보낼 수 있다`() =
        runTest {
            var observedAccept: String? = null
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            observedAccept = request.headers[HttpHeaders.Accept]
                            delay(50)
                            respond("stream-ready", headers = jsonHeaders())
                        },
                    config =
                        ApiConfig(
                            baseUrl = "https://api.example.test",
                            timeouts = NetworkTimeouts(requestMillis = 1, connectMillis = 1_000, socketMillis = 1_000),
                        ),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val result =
                    client.requests.execute(
                        ApiRequest(
                            method = HttpMethod.Get,
                            path = "/stream",
                            kind = RequestKind.READ,
                            accept = "text/event-stream",
                            timeoutOverride = ApiRequestTimeoutOverride(requestMillis = 1_000, socketMillis = 2_000),
                        ),
                    ) { it.bodyAsText() }

                assertEquals("text/event-stream", observedAccept)
                assertEquals(ApiResult.Success("stream-ready"), result)
            } finally {
                client.close()
            }
        }

    @Test
    fun `streaming 헤더 대기는 request timeout으로 끝나고 observer에 null을 한 번 알린다`() =
        runTest {
            val observedStatuses = mutableListOf<Int?>()
            val completedAttempts = mutableListOf<Pair<HttpAttemptOutcome, Int?>>()
            val client =
                createApiClient(
                    engine = MockEngine {
                        delay(50)
                        respond("ready", headers = jsonHeaders())
                    },
                    config =
                        ApiConfig(
                            baseUrl = "https://api.example.test",
                            timeouts = NetworkTimeouts(requestMillis = 10, connectMillis = 1_000, socketMillis = 1_000),
                        ),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                    observer = ApiResponseObserver { observedStatuses += it },
                    attemptObserver = ApiAttemptObserver { _, _ ->
                        ApiAttempt { outcome, status, _ -> completedAttempts += outcome to status }
                    },
                )

            try {
                val result = withContext(Dispatchers.Default) {
                    client.requests.executeStreaming(streamRequest()) { it.bodyAsText() }
                }

                val failure = assertIs<NetworkFailure.Transport>(assertIs<ApiResult.Failure>(result).reason)
                assertEquals(TransportFailureReason.TIMEOUT, failure.reason)
                assertEquals(listOf<Int?>(null), observedStatuses)
                assertEquals(
                    listOf<Pair<HttpAttemptOutcome, Int?>>(HttpAttemptOutcome.TIMEOUT to null),
                    completedAttempts,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `streaming body는 request timeout을 넘겨도 열린 채 유지된다`() =
        runTest {
            val body = ByteChannel()
            body.writeStringUtf8("first chunk\n")
            val headersObserved = CompletableDeferred<Unit>()
            val client =
                createApiClient(
                    engine = MockEngine { respond(body, headers = jsonHeaders()) },
                    config =
                        ApiConfig(
                            baseUrl = "https://api.example.test",
                            timeouts = NetworkTimeouts(requestMillis = 10, connectMillis = 1_000, socketMillis = 1_000),
                        ),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                    observer = ApiResponseObserver { headersObserved.complete(Unit) },
                )

            try {
                withContext(Dispatchers.Default) {
                    coroutineScope {
                        val execution = launch { client.requests.executeStreaming(streamRequest()) { it.bodyAsText() } }
                        headersObserved.await()
                        delay(100)

                        assertTrue(execution.isActive, "The stream must remain open after the header watchdog ends")
                        body.close()
                        execution.join()
                        assertTrue(execution.isCompleted)
                    }
                }
            } finally {
                body.cancel(null)
                client.close()
            }
        }

    @Test
    fun `streaming cancellation after headers propagates and the next request succeeds`() =
        runTest {
            val body = ByteChannel()
            val headersObserved = CompletableDeferred<Unit>()
            var requestCount = 0
            val client =
                createApiClient(
                    engine = MockEngine {
                        requestCount++
                        if (requestCount == 1) {
                            respond(body, headers = jsonHeaders())
                        } else {
                            respond("next", headers = jsonHeaders())
                        }
                    },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                    observer =
                        ApiResponseObserver {
                            if (it == HttpStatusCode.OK.value) headersObserved.complete(Unit)
                        },
                )

            try {
                withContext(Dispatchers.Default) {
                    coroutineScope {
                        val stream = launch { client.requests.executeStreaming(streamRequest()) { it.bodyAsText() } }
                        headersObserved.await()
                        stream.cancelAndJoin()

                        val next = client.requests.executeStreaming(streamRequest("/next")) { it.bodyAsText() }
                        assertEquals(ApiResult.Success("next"), next)
                        assertEquals(2, requestCount)
                    }
                }
            } finally {
                body.cancel(null)
                client.close()
            }
        }

    @Test
    fun `streaming AUTH-001은 한 번 복구 재시도하고 두 응답을 observer에 기록한다`() =
        runTest {
            var requestCount = 0
            val observedStatuses = mutableListOf<Int?>()
            val attemptResults = mutableListOf<Pair<HttpAttemptOutcome, Int?>>()
            val authorizationHeaders = mutableListOf<String?>()
            val tokenProvider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("old-token", generation = 7)

                    override suspend fun recover(rejected: AccessToken): AccessToken {
                        assertEquals("old-token", rejected.value)
                        return AccessToken("new-token", generation = 7)
                    }
                }
            val client =
                createApiClient(
                    engine = MockEngine { request ->
                        requestCount++
                        authorizationHeaders += request.headers[HttpHeaders.Authorization]
                        if (requestCount == 1) {
                            respond(
                                """{"code":"AUTH-001","message":"refresh"}""",
                                status = HttpStatusCode.Unauthorized,
                                headers = jsonHeaders(),
                            )
                        } else {
                            respond("stream ready", headers = jsonHeaders())
                        }
                    },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = tokenProvider,
                    observer = ApiResponseObserver { observedStatuses += it },
                    attemptObserver = ApiAttemptObserver { _, _ ->
                        ApiAttempt { outcome, status, _ -> attemptResults += outcome to status }
                    },
                )

            try {
                val result = withContext(Dispatchers.Default) {
                    client.requests.executeStreaming(streamRequest()) { it.bodyAsText() }
                }

                assertEquals(ApiResult.Success("stream ready"), result)
                assertEquals(2, requestCount)
                assertEquals(listOf<String?>("Bearer old-token", "Bearer new-token"), authorizationHeaders)
                assertEquals(listOf<Int?>(401, 200), observedStatuses)
                assertEquals(
                    listOf(
                        HttpAttemptOutcome.RESPONSE to 401,
                        HttpAttemptOutcome.RESPONSE to 200,
                    ),
                    attemptResults,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `streaming expected session generation과 DEVICE-004 무재시도를 유지한다`() =
        runTest {
            var requestCount = 0
            var recoverCalls = 0
            var invalidatedToken: AccessToken? = null
            val tokenProvider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("current-token", generation = 7)

                    override suspend fun recover(rejected: AccessToken): AccessToken {
                        recoverCalls++
                        return AccessToken("renewed-token", generation = 7)
                    }

                    override suspend fun deviceUnavailable(rejected: AccessToken) {
                        invalidatedToken = rejected
                    }
                }
            val client =
                createApiClient(
                    engine = MockEngine {
                        requestCount++
                        respond(
                            """{"code":"DEVICE-004","message":"device unavailable"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders(),
                        )
                    },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = tokenProvider,
                )

            try {
                val wrongGeneration =
                    withContext(Dispatchers.Default) {
                        client.requests.executeStreaming(streamRequest().copy(expectedSessionGeneration = 8)) {
                            it.bodyAsText()
                        }
                    }
                assertIs<NetworkFailure.SessionUnavailable>(assertIs<ApiResult.Failure>(wrongGeneration).reason)
                assertEquals(0, requestCount)

                val deviceUnavailable =
                    withContext(Dispatchers.Default) {
                        client.requests.executeStreaming(streamRequest()) { it.bodyAsText() }
                    }
                val failure = assertIs<NetworkFailure.HttpStatus>(assertIs<ApiResult.Failure>(deviceUnavailable).reason)
                assertEquals("DEVICE-004", failure.error?.code)
                assertEquals(1, requestCount)
                assertEquals(0, recoverCalls)
                assertEquals("current-token", invalidatedToken?.value)
            } finally {
                client.close()
            }
        }

    @Test
    fun `streaming HTTP 오류에 JSON error와 Retry-After를 보존한다`() =
        runTest {
            val client =
                createClient(
                    MockEngine {
                        respond(
                            content = """{"code":"RATE_LIMITED","message":"slow down"}""",
                            status = HttpStatusCode.TooManyRequests,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.RetryAfter to listOf("3"),
                            ),
                        )
                    },
                )

            try {
                val result = withContext(Dispatchers.Default) {
                    client.requests.executeStreaming(streamRequest()) { it.bodyAsText() }
                }

                assertEquals(
                    NetworkFailure.HttpStatus(
                        statusCode = 429,
                        error = ApiError("RATE_LIMITED", "slow down"),
                        retryAfter = "3",
                        mutationCertainty = MutationCertainty.NOT_A_MUTATION,
                    ),
                    assertIs<ApiResult.Failure>(result).reason,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `연결 실패는 쓰기 반영 여부를 불확실하게 보고한다`() =
        runTest {
            val client = createClient(MockEngine { throw IOException("Connection failed") })
            try {
                val result = client.requests.executeNoContent(ApiRequest(HttpMethod.Post, "/write", RequestKind.WRITE))
                val failure = assertIs<NetworkFailure.Transport>(assertIs<ApiResult.Failure>(result).reason)
                assertEquals(TransportFailureReason.CONNECTION, failure.reason)
                assertEquals(MutationCertainty.UNKNOWN, failure.mutationCertainty)
            } finally {
                client.close()
            }
        }

    private fun createClient(
        engine: MockEngine,
        tokenProvider: AccessTokenProvider? = AccessTokenProvider { AccessToken("test-token") },
    ): ApiClient =
        createApiClient(
            engine = engine,
            config = ApiConfig("https://api.example.test"),
            accessTokenProvider = tokenProvider,
        )

    private fun readRequest(authentication: AuthenticationRequirement = AuthenticationRequirement.REQUIRED) =
        ApiRequest(
            method = HttpMethod.Get,
            path = "/ping",
            kind = RequestKind.READ,
            authentication = authentication,
        )

    private fun streamRequest(path: String = "/stream") =
        ApiRequest(
            method = HttpMethod.Get,
            path = path,
            kind = RequestKind.READ,
            monitoringEndpoint = "test_stream",
            accept = "text/event-stream",
            timeoutOverride = ApiRequestTimeoutOverride(requestMillis = 1_000, socketMillis = 10_000),
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Serializable
    private data class PingResponse(
        val ok: Boolean,
    )
}
