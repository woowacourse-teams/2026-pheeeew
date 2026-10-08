package com.pheeeew.data.repository.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.emotion.EmotionApi
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.emotion.EmotionResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EmotionRepositoryTest {
    @Test
    fun `전체는 bounds만 다음 페이지는 cursor만 보내며 인증과 isMine을 보존한다`() =
        runTest {
            var index = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals("/api/v1/emotions", request.url.encodedPath)
                        if (index++ == 0) {
                            assertEquals(
                                setOf("minLongitude", "minLatitude", "maxLongitude", "maxLatitude"),
                                request.url.parameters.names(),
                            )
                            assertEquals("170.0", request.url.parameters["minLongitude"])
                            assertEquals("-170.0", request.url.parameters["maxLongitude"])
                            assertNull(request.url.parameters["groupId"])
                        } else {
                            assertEquals(setOf("cursor"), request.url.parameters.names())
                        }
                        respond(PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                    ApiConfig("https://api.example.test"),
                    { AccessToken("token") },
                )
            try {
                val repository = EmotionRepositoryImpl(EmotionApi(client.requests))
                val page =
                    assertIs<EmotionResult.Success<*>>(
                        repository.firstPage(EmotionBounds(170.0, 30.0, -170.0, 40.0), null),
                    ).value
                val item = (page as com.pheeeew.domain.model.emotion.EmotionPage).items.single()
                assertEquals(true, item.isMine)
                assertEquals(EmotionState.DISCOURAGED, item.state)
                assertEquals(6, item.reactions.size)
                assertEquals(3000000000L, item.reactions.first().count)
                assertEquals(GeoCoordinate(37.55, 127.02), item.coordinate)
                repository.nextPage("opaque")
                assertEquals(2, index)
            } finally {
                client.close()
            }
        }

    @Test
    fun `반응 선택 취소는 빈 본문 204를 성공으로 처리한다`() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val client =
                createApiClient(
                    MockEngine { request ->
                        methods += request.method
                        assertEquals("/api/v1/emotions/42/emojis/DIZZY", request.url.encodedPath)
                        respond("", HttpStatusCode.NoContent)
                    },
                    ApiConfig("https://api.example.test"),
                    { AccessToken("token") },
                )
            try {
                val repository = EmotionRepositoryImpl(EmotionApi(client.requests))
                assertIs<EmotionResult.Success<Unit>>(repository.react(42, EmotionReactionType.DIZZY, true))
                assertIs<EmotionResult.Success<Unit>>(repository.react(42, EmotionReactionType.DIZZY, false))
                assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), methods)
            } finally {
                client.close()
            }
        }

    @Test
    fun `hasNext인데 커서가 없으면 계약 실패로 처리한다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine {
                        respond(
                            """{"items":[],"hasNext":true,"nextCursor":null}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    ApiConfig("https://api.example.test"),
                    { AccessToken("token") },
                )
            try {
                assertIs<EmotionResult.Failure>(EmotionRepositoryImpl(EmotionApi(client.requests)).nextPage("cursor"))
            } finally {
                client.close()
            }
        }

    @Test
    fun `그룹 감정 목록은 v3 경로에서 그룹과 커서를 유지하며 메모와 오디오를 매핑한다`() =
        runTest {
            var index = 0
            val groupId = "0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39"
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("/api/v3/emotions", request.url.encodedPath)
                        if (index++ == 0) {
                            assertEquals(setOf("groupId"), request.url.parameters.names())
                            assertEquals(groupId, request.url.parameters["groupId"])
                            respond(FEED_PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        } else {
                            assertEquals(setOf("groupId", "cursor"), request.url.parameters.names())
                            assertEquals(groupId, request.url.parameters["groupId"])
                            assertEquals("opaque-cursor", request.url.parameters["cursor"])
                            respond(EMPTY_PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    },
                    ApiConfig("https://api.example.test"),
                    { AccessToken("token") },
                )
            try {
                val repository = EmotionRepositoryImpl(EmotionApi(client.requests))
                val firstPage =
                    assertIs<EmotionPage>(
                        assertIs<EmotionResult.Success<*>>(repository.feedPage(groupId, null)).value,
                    )

                assertEquals(
                    listOf(EmotionContentType.MEMO, EmotionContentType.AUDIO),
                    firstPage.items.map { it.contentType },
                )
                assertEquals("그룹에 남긴 마음", firstPage.items.first().memo)
                val audio = firstPage.items[1].audio
                assertEquals(
                    "https://audio.example.test/play",
                    audio?.url,
                )
                assertEquals("opaque-cursor", firstPage.nextCursor)
                assertIs<EmotionResult.Success<*>>(repository.feedPage(groupId, firstPage.nextCursor))
                assertEquals(2, index)
            } finally {
                client.close()
            }
        }

    @Test
    fun `그룹 감정 목록에서 본문이 빠진 항목은 응답 계약 오류로 처리한다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine {
                        respond(MISSING_AUDIO_PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                    ApiConfig("https://api.example.test"),
                    { AccessToken("token") },
                )
            try {
                val result = EmotionRepositoryImpl(EmotionApi(client.requests)).feedPage("group-id", null)

                assertIs<EmotionResult.Failure>(result)
            } finally {
                client.close()
            }
        }

    private companion object {
        val PAGE =
            """
            {"items":[{"id":42,"geometry":{"type":"Point","coordinates":[127.02,37.55]},"properties":{
              "state":"DISCOURAGED","nickname":"나","createdAt":"2026-09-27T00:00:00Z",
              "isMine":true,"contentType":"MEMO","memo":"메모",
              "emojis":[{"type":"HEART","count":3000000000,"selected":true}]
            }}],"hasNext":false,"nextCursor":null}
            """.trimIndent()

        val FEED_PAGE =
            """
            {
              "items": [
                {"id": 101, "properties": {
                  "state": "DISCOURAGED", "nickname": "나", "createdAt": "2026-09-27T00:00:00Z",
                  "isMine": true, "contentType": "MEMO", "memo": "그룹에 남긴 마음",
                  "emojis": [{"type": "HEART", "count": 3, "selected": true}],
                  "groupStamp": {"text": "버티자", "textColor": "#FFFFFF", "backgroundColor": "#4A90D9", "frame": "SCALLOP"}
                }},
                {"id": 102, "properties": {
                  "state": "ANGRY", "nickname": "동료", "createdAt": "2026-09-27T00:00:00Z",
                  "isMine": false, "contentType": "AUDIO", "emojis": [],
                  "audio": {"playbackUrl": "https://audio.example.test/play", "expiresAt": "2030-01-01T00:00:00Z"}
                }}
              ],
              "hasNext": true,
              "nextCursor": "opaque-cursor"
            }
            """.trimIndent()

        const val EMPTY_PAGE = """{"items":[],"hasNext":false,"nextCursor":null}"""

        val MISSING_AUDIO_PAGE =
            """
            {"items":[{"id":103,"properties":{
              "state":"ANGRY","nickname":"동료","createdAt":"2026-09-27T00:00:00Z",
              "isMine":false,"contentType":"AUDIO","emojis":[]
            }}],"hasNext":false,"nextCursor":null}
            """.trimIndent()
    }
}
