package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.block.EmotionBlockApi
import com.pheeeew.data.remote.block.UserBlockApi
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.repository.EmotionModerationRepositoryImpl
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmotionDeleteApiTest {
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
                    EmotionModerationRepositoryImpl(
                        EmotionReportApi(client.requests),
                        EmotionBlockApi(client.requests),
                        UserBlockApi(client.requests),
                        EmotionDeleteApi(client.requests),
                    )
                assertEquals(EmotionModerationResult.Success, DeleteEmotionUseCase(repository)(42))
            } finally {
                client.close()
            }
        }
}
