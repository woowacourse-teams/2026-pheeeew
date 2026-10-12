package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionRegionMapApiTest {
    @Test
    fun `확대와 작은 이동 및 계층 왕복은 조회 영역 캐시를 재사용한다`() = runTest {
        var calls = 0
        val client = createApiClient(
            engine = MockEngine {
                calls++
                respond(
                    """[{"type":"Feature","id":"a","geometry":{"type":"Point","coordinates":[128,38]},"properties":{"name":"지역","count":3}}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            config = ApiConfig("https://api-dev.pheeeew.com"),
            accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
        )
        try {
            val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
            val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
            val moved = EmotionMapBounds(126.95, 37.52, 127.15, 37.62)
            val zoomed = EmotionMapBounds(127.0, 37.53, 127.05, 37.57)
            val initial = repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
            assertEquals(initial, repository.findRegions(moved, EmotionRegionLevel.SIGUNGU))
            assertEquals(initial, repository.findRegions(zoomed, EmotionRegionLevel.SIGUNGU))
            assertEquals(1, calls)
            // Display points outside the viewport are deliberately retained.
            assertEquals(128.0, repository.findSnapshot(zoomed, EmotionRegionLevel.SIGUNGU)?.single()?.longitude)
            repository.findRegions(area, EmotionRegionLevel.SIDO)
            assertEquals(2, calls)
            repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
            assertEquals(2, calls)
            repository.findRegions(EmotionMapBounds(129.0, 35.0, 129.2, 35.1), EmotionRegionLevel.SIGUNGU)
            assertEquals(3, calls)
            repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
            assertEquals(3, calls)
            repository.findRegions(area, EmotionRegionLevel.SIGUNGU, forceRefresh = true)
            repository.findRegions(area, EmotionRegionLevel.SIDO)
            assertEquals(5, calls)
            repository.findRegions(area, EmotionRegionLevel.SIGUNGU, groupId = "group")
            assertEquals(6, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `재방문은 요청을 생략하고 새로고침의 빈 결과도 캐시에 반영한다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            calls++
                            respond(
                                if (calls ==
                                    1
                                ) {
                                    """[{"type":"Feature","id":"a","geometry":{"type":"Point","coordinates":[127.02,37.51]},"properties":{"name":"강남구","count":3}}]"""
                                } else {
                                    "[]"
                                },
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                val initial = repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(initial, repository.findRegions(area, EmotionRegionLevel.SIGUNGU))
                assertEquals(1, calls)
                assertEquals(1, repository.findSnapshot(area, EmotionRegionLevel.SIGUNGU)?.size)
                assertEquals(
                    EmotionRegionResult.Success(emptyList()),
                    repository.findRegions(area, EmotionRegionLevel.SIGUNGU, forceRefresh = true),
                )
                assertEquals(
                    EmotionRegionResult.Success(emptyList()),
                    repository.findRegions(area, EmotionRegionLevel.SIGUNGU),
                )
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `지역 API는 경계와 계층을 보내고 요약 피처만 표시한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals("/api/v2/emotions/map/regions", request.url.encodedPath)
                            assertEquals("126.75", request.url.parameters["minLongitude"])
                            assertEquals("37.375", request.url.parameters["minLatitude"])
                            assertEquals("127.25", request.url.parameters["maxLongitude"])
                            assertEquals("37.625", request.url.parameters["maxLatitude"])
                            assertEquals("SIGUNGU", request.url.parameters["level"])
                            respond(
                                """[
                      {"type":"Feature","id":"11680","geometry":{"type":"Point","coordinates":[127.02,37.51]},"properties":{"regionName":"강남구","emotionCount":48,"representativeState":"FRUSTRATED"}},
                      {"type":"Feature","id":"bad","geometry":{"type":"Point","coordinates":[200,37.51]},"properties":{"name":"잘못된 지역","count":1}}
                    ]""",
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val result =
                    EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests)).findRegions(
                        EmotionMapBounds(126.9, 37.5, 127.1, 37.6),
                        EmotionRegionLevel.SIGUNGU,
                    )
                val regions = assertIs<EmotionRegionResult.Success>(result).regions
                assertEquals(1, regions.size)
                assertEquals("강남구", regions.single().name)
                assertEquals(48L, regions.single().count)
                assertEquals(EmotionState.FRUSTRATED, regions.single().representativeState)
            } finally {
                client.close()
            }
        }
}
