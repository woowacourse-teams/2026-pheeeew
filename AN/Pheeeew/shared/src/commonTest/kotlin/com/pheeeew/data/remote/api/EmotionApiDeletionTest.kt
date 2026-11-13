package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.repository.EmotionFailure
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.EmotionResult
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmotionApiDeletionTest {
    @Test
    fun `본인 감정 삭제는 인증된 DELETE 요청의 204를 성공으로 처리한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Delete, request.method)
                            assertEquals("/api/v1/emotions/42", request.url.encodedPath)
                            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                            respond("", status = HttpStatusCode.NoContent)
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository =
                    EmotionRepositoryImpl(EmotionApi(client.requests))
                assertEquals(EmotionModerationResult.Success, DeleteEmotionUseCase(repository)(42))
            } finally {
                client.close()
            }
        }

    @Test
    fun `session change prevents pending collection reaction and deletion from being sent`() =
        runTest {
            var transmitted = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            transmitted++
                            respond("", status = HttpStatusCode.NoContent)
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("new-device-token", generation = 2) },
                )
            try {
                val repository = EmotionRepositoryImpl(EmotionApi(client.requests))
                val oldGeneration = 1L
                assertEquals(
                    EmotionResult.Failure(EmotionFailure.AUTHENTICATION),
                    repository.react(42, EmotionReactionType.HEART, true, oldGeneration),
                )
                assertEquals(EmotionModerationResult.Unavailable, DeleteEmotionUseCase(repository)(42, oldGeneration))
                assertEquals(0, transmitted)
            } finally {
                client.close()
            }
        }
}
