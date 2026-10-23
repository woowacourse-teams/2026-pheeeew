package com.pheeeew.data.repository

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.api.EmotionApi
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.repository.EmotionResult
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
    fun `필터 목록은 v4에서 좌표 없이 조회하고 다음 페이지는 커서만 전달한다`() =
        runTest {
            for (scope in MapFilterScope.entries) {
                for (content in MapFilterContent.entries) {
                    val filter =
                        EmotionSearchFilter(
                            startDate = "2026-10-01",
                            endDate = "2026-10-11",
                            content = content,
                            scope = scope,
                        )
                    var count = 0
                    val client =
                        createApiClient(
                            MockEngine { request ->
                                assertEquals("/api/v4/emotions", request.url.encodedPath)
                                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                                val expected =
                                    if (count++ == 0) {
                                        buildMap {
                                            put("startDate", "2026-10-01")
                                            put("endDate", "2026-10-11")
                                            put("contentType", content.name)
                                            put("scope", scope.name)
                                        }
                                    } else {
                                        mapOf("cursor" to "opaque+/=cursor")
                                    }
                                assertEquals(expected.keys, request.url.parameters.names())
                                expected.forEach { (key, value) -> assertEquals(value, request.url.parameters[key]) }
                                respond(EMPTY_PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                            ApiConfig("https://api.example.test"),
                            { AccessToken("token") },
                        )
                    try {
                        val repository = EmotionRepositoryImpl(EmotionApi(client.requests))
                        assertIs<EmotionResult.Success<*>>(repository.filteredFirstPage(filter))
                        assertIs<EmotionResult.Success<*>>(repository.nextFilteredPage("opaque+/=cursor"))
                        assertEquals(2, count)
                    } finally {
                        client.close()
                    }
                }
            }
        }

    @Test
    fun `전체는 좌표 없이 다음 페이지는 cursor만 보내며 인증과 isMine을 보존한다`() =
        runTest {
            var index = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals("/api/v4/emotions", request.url.encodedPath)
                        if (index++ == 0) {
                            assertEquals(
                                emptySet(),
                                request.url.parameters.names(),
                            )
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
                        repository.firstPage(EmotionBounds(170.0, 30.0, -170.0, 40.0)),
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
    fun `감정 목록은 v4 경로에서 커서를 유지하며 메모와 오디오를 매핑한다`() =
        runTest {
            var index = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("/api/v4/emotions", request.url.encodedPath)
                        if (index++ == 0) {
                            assertEquals(
                                setOf("startDate", "endDate", "contentType", "scope"),
                                request.url.parameters.names(),
                            )
                            respond(FEED_PAGE, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        } else {
                            assertEquals(setOf("cursor"), request.url.parameters.names())
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
                        assertIs<EmotionResult.Success<*>>(
                            repository.filteredFirstPage(EmotionSearchFilter.DEFAULT),
                        ).value,
                    )

                assertEquals(
                    listOf(EmotionContentType.MEMO, EmotionContentType.AUDIO),
                    firstPage.items.map { it.contentType },
                )
                assertEquals("오늘 남긴 마음", firstPage.items.first().memo)
                val audio = firstPage.items[1].audio
                assertEquals(
                    "https://audio.example.test/play",
                    audio?.url,
                )
                assertEquals("opaque-cursor", firstPage.nextCursor)
                assertIs<EmotionResult.Success<*>>(repository.nextFilteredPage(requireNotNull(firstPage.nextCursor)))
                assertEquals(2, index)
            } finally {
                client.close()
            }
        }

    @Test
    fun `감정 목록에서 본문이 빠진 항목은 응답 계약 오류로 처리한다`() =
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
                val result =
                    EmotionRepositoryImpl(
                        EmotionApi(client.requests),
                    ).filteredFirstPage(EmotionSearchFilter.DEFAULT)

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
                  "isMine": true, "contentType": "MEMO", "memo": "오늘 남긴 마음",
                  "emojis": [{"type": "HEART", "count": 3, "selected": true}]
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
