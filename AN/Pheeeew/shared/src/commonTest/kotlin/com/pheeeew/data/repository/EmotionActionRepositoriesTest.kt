package com.pheeeew.data.repository

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.api.BlockApi
import com.pheeeew.data.remote.api.EmotionApi
import com.pheeeew.data.remote.api.ReportApi
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.usecase.ReportEmotionUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmotionActionRepositoriesTest {
    @Test
    fun `신고는 독립 Repository를 통해 요청 본문과 인증을 전달한다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("/api/v2/reports", request.url.encodedPath)
                        assertEquals("Bearer token", request.headers["Authorization"])
                        assertEquals(
                            "{\"emotionId\":42,\"reason\":\"spam\"}",
                            request.body.toByteArray().decodeToString(),
                        )
                        respond(
                            """{"id":1,"emotionId":42,"reason":"spam","createdAt":"2026-10-19T00:00:00Z"}""",
                            HttpStatusCode.Created,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                assertEquals(
                    EmotionModerationResult.Success,
                    ReportEmotionUseCase(ReportRepositoryImpl(ReportApi(client.requests)))(42, "spam"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `삭제 차단 신고의 기존 업무 오류 결과를 보존한다`() =
        runTest {
            val cases =
                listOf(
                    Triple("BLOCK-002", HttpStatusCode.BadRequest, EmotionModerationResult.OwnEmotion),
                    Triple("BLOCK-003", HttpStatusCode.BadRequest, EmotionModerationResult.AuthorUnknown),
                    Triple("REPORT-002", HttpStatusCode.BadRequest, EmotionModerationResult.OwnEmotion),
                    Triple("EMOTION-001", HttpStatusCode.NotFound, EmotionModerationResult.NotFound),
                    Triple("SERVER-001", HttpStatusCode.InternalServerError, EmotionModerationResult.Unavailable),
                )
            for ((code, status, expected) in cases) {
                val client =
                    createApiClient(
                        MockEngine {
                            respond(
                                """{"code":"$code"}""",
                                status,
                                headersOf("Content-Type", "application/json"),
                            )
                        },
                        ApiConfig("https://example.com"),
                        AccessTokenProvider { AccessToken("token") },
                    )
                try {
                    for (action in actions(client.requests)) assertEquals(expected, action())
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `삭제 차단 신고는 AUTH-001에 한 번만 갱신하고 반복 실패를 반환한다`() =
        runTest {
            var calls = 0
            var recoveries = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("old")

                    override suspend fun recover(rejected: AccessToken): AccessToken {
                        recoveries++
                        return AccessToken("new")
                    }
                }
            val client =
                createApiClient(
                    MockEngine {
                        calls++
                        respond(
                            """{"code":"AUTH-001"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                for (action in actions(client.requests)) assertEquals(EmotionModerationResult.Unavailable, action())
                assertEquals(8, calls)
                assertEquals(4, recoveries)
            } finally {
                client.close()
            }
        }

    @Test
    fun `삭제 차단 신고의 취소는 일반 오류로 변환하지 않는다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { throw CancellationException("owner disposed") },
                    ApiConfig("https://example.com"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                for (action in actions(client.requests)) assertFailsWith<CancellationException> { action() }
            } finally {
                client.close()
            }
        }

    @Test
    fun `차단 성공 응답이 계약을 위반하면 성공으로 처리하지 않는다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { respond("{}", HttpStatusCode.Created, headersOf("Content-Type", "application/json")) },
                    ApiConfig("https://example.com"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                val blocks = BlockRepositoryImpl(BlockApi(client.requests))
                assertEquals(EmotionModerationResult.Unavailable, blocks.blockEmotion(42))
                assertEquals(EmotionModerationResult.Unavailable, blocks.blockUser(42))
            } finally {
                client.close()
            }
        }

    @Test
    fun `삭제 차단 신고는 AUTH-001 갱신 후 같은 요청을 성공시킨다`() =
        runTest {
            var calls = 0
            var recoveries = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("old")

                    override suspend fun recover(rejected: AccessToken): AccessToken {
                        recoveries++
                        return AccessToken("new")
                    }
                }
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        if (request.headers["Authorization"] == "Bearer old") {
                            respond(
                                """{"code":"AUTH-001"}""",
                                HttpStatusCode.Unauthorized,
                                headersOf("Content-Type", "application/json"),
                            )
                        } else {
                            assertEquals("Bearer new", request.headers["Authorization"])
                            val response =
                                when (request.url.encodedPath) {
                                    "/api/v2/blocks/emotions" -> {
                                        """{"emotionId":42,"nickname":"n","createdAt":"2026-10-19T00:00:00Z"}"""
                                    }

                                    "/api/v2/blocks/devices" -> {
                                        """{"blockId":7,"emotionId":99,"nickname":"n","createdAt":"2026-10-19T00:00:00Z"}"""
                                    }

                                    "/api/v2/reports" -> {
                                        """{"id":7,"emotionId":42,"reason":"original reason","createdAt":"2026-10-19T00:00:00Z"}"""
                                    }

                                    else -> {
                                        ""
                                    }
                                }
                            respond(
                                response,
                                if (response.isEmpty()) HttpStatusCode.NoContent else HttpStatusCode.OK,
                                headersOf("Content-Type", "application/json"),
                            )
                        }
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                for (action in actions(client.requests)) assertEquals(EmotionModerationResult.Success, action())
                assertEquals(8, calls)
                assertEquals(4, recoveries)
            } finally {
                client.close()
            }
        }

    @Test
    fun `DEVICE-004는 갱신 없이 기기 소유자에게 알리고 재전송하지 않는다`() =
        runTest {
            var calls = 0
            var invalidations = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("old")

                    override suspend fun recover(rejected: AccessToken): AccessToken =
                        error("DEVICE-004 must not refresh")

                    override suspend fun deviceUnavailable(rejected: AccessToken) {
                        invalidations++
                    }
                }
            val client =
                createApiClient(
                    MockEngine {
                        calls++
                        respond(
                            """{"code":"DEVICE-004"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                for (action in actions(client.requests)) assertEquals(EmotionModerationResult.Unavailable, action())
                assertEquals(4, calls)
                assertEquals(4, invalidations)
            } finally {
                client.close()
            }
        }

    @Test
    fun `서버 오류는 인증 갱신이나 쓰기 재전송으로 처리하지 않는다`() =
        runTest {
            var calls = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("token")

                    override suspend fun recover(rejected: AccessToken): AccessToken = error("5xx must not refresh")
                }
            val client =
                createApiClient(
                    MockEngine {
                        calls++
                        respond("{}", HttpStatusCode.InternalServerError, headersOf("Content-Type", "application/json"))
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                for (action in actions(client.requests)) assertEquals(EmotionModerationResult.Unavailable, action())
                assertEquals(4, calls)
            } finally {
                client.close()
            }
        }

    private fun actions(requests: ApiRequestExecutor): List<suspend () -> EmotionModerationResult> {
        val emotions = EmotionRepositoryImpl(EmotionApi(requests))
        val blocks = BlockRepositoryImpl(BlockApi(requests))
        val reports = ReportRepositoryImpl(ReportApi(requests))
        return listOf(
            { emotions.delete(42) },
            { blocks.blockEmotion(42) },
            { blocks.blockUser(42) },
            { reports.report(42, "spam") },
        )
    }
}
