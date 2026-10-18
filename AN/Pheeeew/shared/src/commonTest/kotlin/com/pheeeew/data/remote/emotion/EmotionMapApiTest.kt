package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.group.GroupStampFrame
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionMapApiTest {
    @Test
    fun `세션 무효화는 캐시를 비우고 이전 조회가 캐시를 다시 채우지 못하게 한다`() =
        runTest {
            var requests = 0
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client =
                createApiClient(
                    MockEngine {
                        if (++requests == 2) {
                            started.complete(Unit)
                            release.await()
                        }
                        respond(
                            """{"items":[],"hasNext":false,"nextCursor":null}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                val repository = EmotionMapRepositoryImpl(EmotionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                repository.findPage(area, groupId = null, cursor = null)
                repository.findPage(area, groupId = null, cursor = null)
                assertEquals(1, requests)
                val oldRequest = async { repository.findPage(area, groupId = null, cursor = null, forceRefresh = true) }
                started.await()
                repository.invalidateSession()
                release.complete(Unit)
                oldRequest.await()
                assertEquals(null, repository.findSnapshot(area, groupId = null))
                repository.findPage(area, groupId = null, cursor = null)
                assertEquals(3, requests)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }

    @Test
    fun `지도 첫 페이지는 모든 필터 조합을 보내고 다음 페이지는 커서로 복원한다`() =
        runTest {
            for (scope in MapFilterScope.entries) {
                for (content in MapFilterContent.entries) {
                    for (dated in listOf(false, true)) {
                        val filter =
                            EmotionSearchFilter(
                                startDate = "2026-10-01".takeIf { dated },
                                endDate = "2026-10-11".takeIf { dated },
                                content = content,
                                scope = scope,
                                groupId =
                                    "11111111-1111-4111-8111-111111111111".takeIf {
                                        scope == MapFilterScope.GROUP
                                    },
                            )
                        var count = 0
                        val client =
                            createApiClient(
                                MockEngine { request ->
                                    assertEquals("/api/v2/emotions/map", request.url.encodedPath)
                                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                                    val expected =
                                        if (count++ == 0) {
                                            buildMap {
                                                put("minLongitude", "126.9")
                                                put("minLatitude", "37.5")
                                                put("maxLongitude", "127.1")
                                                put("maxLatitude", "37.6")
                                                put("contentType", content.name)
                                                put("scope", scope.name)
                                                filter.startDate?.let { put("startDate", it) }
                                                filter.endDate?.let { put("endDate", it) }
                                                filter.groupId?.let { put("groupId", it) }
                                            }
                                        } else {
                                            mapOf("cursor" to "opaque+/=cursor")
                                        }
                                    assertEquals(expected.keys, request.url.parameters.names())
                                    expected.forEach { (key, value) ->
                                        assertEquals(value, request.url.parameters[key])
                                    }
                                    respond(
                                        """{"items":[],"hasNext":false,"nextCursor":null}""",
                                        headers = jsonHeaders(),
                                    )
                                },
                                ApiConfig("https://api.example.test"),
                                AccessTokenProvider { AccessToken("token") },
                            )
                        try {
                            val api = EmotionMapApi(client.requests)
                            assertIs<ApiResult.Success<*>>(api.findPage(126.9, 37.5, 127.1, 37.6, null, null, filter))
                            assertIs<ApiResult.Success<*>>(
                                api.findPage(null, null, null, null, null, "opaque+/=cursor", filter),
                            )
                            assertEquals(2, count)
                        } finally {
                            client.close()
                        }
                    }
                }
            }
        }

    @Test
    fun `첫 페이지는 경계 값을 보내고 다음 페이지는 cursor만 보낸다`() =
        runTest {
            var requestNumber = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("/api/v2/emotions/map", request.url.encodedPath)
                            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                            if (requestNumber++ == 0) {
                                assertEquals("126.9", request.url.parameters["minLongitude"])
                                assertEquals("37.5", request.url.parameters["minLatitude"])
                                assertEquals("127.1", request.url.parameters["maxLongitude"])
                                assertEquals("37.6", request.url.parameters["maxLatitude"])
                                assertEquals("group-123", request.url.parameters["groupId"])
                                assertEquals(null, request.url.parameters["cursor"])
                                respond(
                                    """{"items":[],"hasNext":true,"nextCursor":"page-2"}""",
                                    headers = jsonHeaders(),
                                )
                            } else {
                                assertEquals(setOf("cursor"), request.url.parameters.names())
                                assertEquals("page-2", request.url.parameters["cursor"])
                                respond(
                                    """{"items":[],"hasNext":false,"nextCursor":null}""",
                                    headers = jsonHeaders(),
                                )
                            }
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val api = EmotionMapApi(client.requests)
                val first =
                    api.findPage(126.9, 37.5, 127.1, 37.6, "group-123", cursor = null)
                assertIs<ApiResult.Success<EmotionMapPageDto>>(first)
                assertEquals("page-2", first.value.nextCursor)

                val second =
                    api.findPage(null, null, null, null, groupId = null, cursor = "page-2")
                assertIs<ApiResult.Success<EmotionMapPageDto>>(second)
                assertEquals(false, second.value.hasNext)
                assertEquals(2, requestNumber)
            } finally {
                client.close()
            }
        }

    @Test
    fun `지도 API가 좌표와 스탬프 DTO를 파싱한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            respond(
                                """
                                {
                                  "items": [
                                    {
                                      "type": "Feature",
                                      "id": 9007199254740993,
                                      "geometry": {"type": "Point", "coordinates": [127.1234, 37.5678]},
                                      "properties": {
                                        "createdAt": "2026-09-24T12:00:00Z",
                                        "state": "DISCOURAGED",
                                        "rotationDegrees": 35.5,
                                        "groupStamp": {
                                          "text": "쉼",
                                          "textColor": "#FFFFFF",
                                          "backgroundColor": "#11223344",
                                          "frame": "VOUCHER"
                                        }
                                      }
                                    },
                                    {
                                      "type": "Feature",
                                      "id": 2,
                                      "geometry": {"type": "Point", "coordinates": [181, 37.5]},
                                      "properties": {
                                        "createdAt": "2026-09-24T12:00:00Z",
                                        "state": "FRUSTRATED",
                                        "rotationDegrees": 0,
                                        "groupStamp": null
                                      }
                                    }
                                  ],
                                  "hasNext": false,
                                  "nextCursor": null
                                }
                                """.trimIndent(),
                                headers = jsonHeaders(),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val api = EmotionMapApi(client.requests)
                val result =
                    api.findPage(127.0, 37.4, 127.2, 37.7, groupId = null, cursor = null)

                val page = assertIs<ApiResult.Success<EmotionMapPageDto>>(result).value
                assertEquals(2, page.items.size)
                val validFeature = page.items.first()
                assertEquals(9007199254740993L, validFeature.id)
                assertEquals(listOf(127.1234, 37.5678), validFeature.geometry.coordinates)
                assertEquals("DISCOURAGED", validFeature.properties.state)
                assertEquals(35.5, validFeature.properties.rotationDegrees)
                assertEquals("VOUCHER", validFeature.properties.groupStamp?.frame)
                assertEquals("#11223344", validFeature.properties.groupStamp?.backgroundColor)
                assertEquals(181.0, page.items[1].geometry.coordinates[0])
            } finally {
                client.close()
            }
        }

    @Test
    fun `API 조회에 실패하면 임시 핀을 대신 반환하지 않는다`() =
        runTest {
            var requestCount = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requestCount++
                            error("API unavailable")
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )

            try {
                val repository = EmotionMapRepositoryImpl(EmotionMapApi(client.requests))
                val result =
                    repository.findPage(
                        EmotionMapBounds(127.145, 37.439, 127.150, 37.443),
                        groupId = null,
                        cursor = null,
                    )

                assertIs<EmotionMapPageResult.Failure>(result)
                assertEquals(1, requestCount)
            } finally {
                client.close()
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/geo+json")
}
