package com.pheeeew.data.remote.block

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.emotion.EmotionDeleteApi
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.repository.EmotionModerationRepositoryImpl
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.usecase.BlockUserUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionBlockApiTest {
    @Test
    fun `감정 차단은 인증된 감정 ID를 보내고 응답을 읽는다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Post, request.method)
                            assertEquals("/api/v2/blocks/emotions", request.url.encodedPath)
                            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                            assertEquals("{\"emotionId\":42}", request.body.toByteArray().decodeToString())
                            respond(
                                """{"emotionId":42,"nickname":"익명","memo":null,"createdAt":"2026-09-28T00:00:00Z"}""",
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val result = EmotionBlockApi(client.requests).create(42)
                assertEquals(42L, assertIs<ApiResult.Success<EmotionBlockResponseDto>>(result).value.emotionId)
            } finally {
                client.close()
            }
        }

    @Test
    fun `차단 요청은 사용자 차단 API를 호출한다`() =
        runTest {
            val requestedPaths = mutableListOf<String>()
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPaths += request.url.encodedPath
                            assertEquals("{\"emotionId\":42}", request.body.toByteArray().decodeToString())
                            respond(
                                """{"blockId":7,"emotionId":42,"nickname":"익명","memo":null,"createdAt":"2026-09-28T00:00:00Z"}""",
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository =
                    EmotionModerationRepositoryImpl(
                        EmotionReportApi(client.requests),
                        EmotionBlockApi(client.requests),
                        UserBlockApi(client.requests),
                        EmotionDeleteApi(client.requests),
                    )
                val block = BlockUserUseCase(repository)
                assertEquals(EmotionModerationResult.Success, block(42))
                assertEquals(EmotionModerationResult.Success, block(42))
                assertEquals(listOf("/api/v2/blocks/devices", "/api/v2/blocks/devices"), requestedPaths)
            } finally {
                client.close()
            }
        }
}
