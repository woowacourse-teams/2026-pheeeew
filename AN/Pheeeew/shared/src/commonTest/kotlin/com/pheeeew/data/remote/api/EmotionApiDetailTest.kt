package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionDetailRepositoryImpl
import com.pheeeew.data.repository.EmotionRepositoryImpl
import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.repository.EmotionResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmotionApiDetailTest {
    @Test
    fun `두 Repository는 동일 상세 API의 GeoJSON을 각 domain 모델로 변환한다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals("/api/v1/emotions/42", request.url.encodedPath)
                        val emojis =
                            EmotionReactionType.entries.joinToString(",") {
                                """{"type":"${it.name}","count":3000000000,"selected":true}"""
                            }
                        respond(
                            """{"id":42,"geometry":{"type":"Point","coordinates":[127.02,37.55]},"properties":{
                    "state":"FRUSTRATED","nickname":"name","createdAt":"2026-10-19T00:00:00Z","isMine":true,
                    "contentType":"MEMO","memo":"memo","emojis":[$emojis],"groupId":"group-id"}}""",
                            headers = headersOf("Content-Type", "application/geo+json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    { AccessToken("token") },
                )
            try {
                val api = EmotionApi(client.requests)
                val detail = assertIs<EmotionDetailResult.Success>(EmotionDetailRepositoryImpl(api).findById(42)).detail
                val list = assertIs<EmotionResult.Success<Emotion>>(EmotionRepositoryImpl(api).detail(42)).value
                assertEquals("group-id", detail.groupId)
                assertEquals(detail.memo, list.memo)
                assertEquals(detail.nickname, list.nickname)
                assertEquals(3000000000L, list.reactions.first().count)
                assertEquals(6, detail.reactions.size)
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `두 Repository의 반응 선택과 취소는 같은 API의 204 처리 경로를 쓴다`() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val client =
                createApiClient(
                    MockEngine { request ->
                        methods += request.method
                        assertEquals("/api/v1/emotions/42/emojis/HEART", request.url.encodedPath)
                        respond("", HttpStatusCode.NoContent)
                    },
                    ApiConfig("https://example.com"),
                    { AccessToken("token") },
                )
            try {
                val api = EmotionApi(client.requests)
                assertTrue(EmotionDetailRepositoryImpl(api).setReactionSelected(42, EmotionReactionType.HEART, true))
                assertIs<EmotionResult.Success<Unit>>(
                    EmotionRepositoryImpl(api).react(42, EmotionReactionType.HEART, false),
                )
                assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), methods)
            } finally {
                client.close()
            }
        }
}
