package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.group.GroupStampFrame
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionMapApiTest {
    @Test
    fun `첫 페이지는 경계 값을 보내고 다음 페이지는 cursor만 보낸다`() =
        runTest {
            var requestNumber = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("/api/v1/emotions/map", request.url.encodedPath)
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
