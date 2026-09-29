package com.pheeeew.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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
                        ApiRequest(HttpMethod.Delete, "/groups/1", RequestKind.WRITE),
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
                        ApiRequest(HttpMethod.Post, "/groups", RequestKind.WRITE),
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
                        ApiRequest(HttpMethod.Post, "/groups", RequestKind.WRITE),
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

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Serializable
    private data class PingResponse(
        val ok: Boolean,
    )
}
