package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.EmotionRegionMapRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.model.emotion.SearchDate
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmotionRegionMapApiTest {
    @Test
    fun `마지막 대기자가 취소되면 공유 중인 HTTP 요청도 취소한다`() =
        runTest {
            var requests = 0
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val client =
                createApiClient(
                    MockEngine {
                        requests++
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (cancellation: CancellationException) {
                            cancelled.complete(Unit)
                            throw cancellation
                        }
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                val request = async { repository.findRegions(area, EmotionRegionLevel.SIGUNGU) }

                started.await()
                request.cancelAndJoin()
                cancelled.await()

                assertEquals(1, requests)
                assertTrue(request.isCancelled)
            } finally {
                client.close()
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `동일한 전국 조건의 동시 요청은 bbox가 달라도 하나로 공유한다`() =
        runTest {
            var requests = 0
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client =
                createApiClient(
                    MockEngine {
                        requests++
                        started.complete(Unit)
                        release.await()
                        respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val seoul = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                val busan = EmotionMapBounds(129.0, 35.0, 129.2, 35.1)
                val first = async { repository.findRegions(seoul, EmotionRegionLevel.SIGUNGU) }
                started.await()
                val second = async { repository.findRegions(busan, EmotionRegionLevel.SIGUNGU) }
                runCurrent()
                assertEquals(1, requests)
                first.cancel()
                first.join()
                assertTrue(first.isCancelled)
                release.complete(Unit)
                assertIs<EmotionRegionResult.Success>(second.await())
                assertEquals(1, requests)
                assertEquals(emptyList(), repository.findSnapshot(EmotionRegionLevel.SIGUNGU))
            } finally {
                release.complete(Unit)
                client.close()
            }
        }

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
                            """[]""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(1, requests)
                val oldRequest = async { repository.findRegions(area, EmotionRegionLevel.SIGUNGU, forceRefresh = true) }
                started.await()
                repository.invalidateSession()
                release.complete(Unit)
                assertEquals(EmotionRegionResult.Failure, oldRequest.await())
                assertEquals(null, repository.findSnapshot(EmotionRegionLevel.SIGUNGU))
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(3, requests)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `전체 무효화와 강제 새로고침은 활성 대기자에게 실패를 반환하고 새 요청은 완료한다`() =
        runTest {
            for (forceRefresh in listOf(false, true)) {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                var requests = 0
                val client =
                    createApiClient(
                        MockEngine {
                            if (++requests == 1) {
                                started.complete(Unit)
                                release.await()
                            }
                            respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                        ApiConfig("https://api.example.test"),
                        AccessTokenProvider { AccessToken("token") },
                    )
                try {
                    val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                    val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                    val first = async { repository.findRegions(area, EmotionRegionLevel.SIGUNGU) }
                    started.await()
                    val second = async { repository.findRegions(area, EmotionRegionLevel.SIGUNGU) }
                    runCurrent()
                    assertEquals(1, requests)

                    if (!forceRefresh) repository.invalidateAll()
                    assertIs<EmotionRegionResult.Success>(
                        repository.findRegions(area, EmotionRegionLevel.SIGUNGU, forceRefresh = forceRefresh),
                    )
                    assertEquals(EmotionRegionResult.Failure, first.await())
                    assertEquals(EmotionRegionResult.Failure, second.await())
                    assertEquals(2, requests)
                    assertEquals(emptyList(), repository.findSnapshot(EmotionRegionLevel.SIGUNGU))
                } finally {
                    release.complete(Unit)
                    client.close()
                }
            }
        }

    @Test
    fun `필터를 집계 요청에 전달하고 다른 필터의 캐시를 재사용하지 않는다`() =
        runTest {
            val today = SearchDate(2026, 10, 13)
            val filters =
                listOf(
                    MapFilterSettings().toSearchFilter(today),
                    MapFilterSettings(period = MapFilterPeriod.ALL).toSearchFilter(today),
                    MapFilterSettings(scope = MapFilterScope.MY, content = MapFilterContent.MEMO).toSearchFilter(today),
                    MapFilterSettings(
                        scope = MapFilterScope.ALL,
                        content = MapFilterContent.AUDIO,
                    ).toSearchFilter(today),
                )
            val expectedQueries =
                listOf(
                    mapOf(
                        "startDate" to "2026-09-13",
                        "endDate" to "2026-10-13",
                        "contentType" to "ALL",
                        "scope" to "ALL",
                    ),
                    mapOf("contentType" to "ALL", "scope" to "ALL"),
                    mapOf(
                        "startDate" to "2026-09-13",
                        "endDate" to "2026-10-13",
                        "contentType" to "MEMO",
                        "scope" to "MY",
                    ),
                    mapOf(
                        "startDate" to "2026-09-13",
                        "endDate" to "2026-10-13",
                        "contentType" to "AUDIO",
                        "scope" to "ALL",
                    ),
                )
            var calls = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            val expected = expectedQueries[calls++]
                            assertEquals(
                                expected,
                                request.url.parameters
                                    .entries()
                                    .filter {
                                        it.key in
                                            setOf(
                                                "startDate",
                                                "endDate",
                                                "contentType",
                                                "scope",
                                                "groupId",
                                            )
                                    }.associate { it.key to it.value.single() },
                            )
                            respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
                filters.forEach { filter ->
                    assertIs<EmotionRegionResult.Success>(
                        repository.findRegions(area, EmotionRegionLevel.SIDO, filter = filter),
                    )
                }
                filters.forEach { filter ->
                    assertIs<EmotionRegionResult.Success>(
                        repository.findRegions(area, EmotionRegionLevel.SIDO, filter = filter),
                    )
                }
                assertEquals(4, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `전국 요약은 bbox와 무관하게 재사용하고 계층별 캐시를 유지한다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
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
                val moved = EmotionMapBounds(129.0, 35.0, 129.2, 35.1)
                val zoomed = EmotionMapBounds(127.0, 37.53, 127.05, 37.57)
                val initial = repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(initial, repository.findRegions(moved, EmotionRegionLevel.SIGUNGU))
                assertEquals(initial, repository.findRegions(zoomed, EmotionRegionLevel.SIGUNGU))
                assertEquals(1, calls)
                // Display points outside the viewport are deliberately retained.
                assertEquals(128.0, repository.findSnapshot(EmotionRegionLevel.SIGUNGU)?.single()?.longitude)
                repository.findRegions(area, EmotionRegionLevel.SIDO)
                assertEquals(2, calls)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(2, calls)
                repository.findRegions(moved, EmotionRegionLevel.SIGUNGU)
                assertEquals(2, calls)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(2, calls)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU, forceRefresh = true)
                repository.findRegions(area, EmotionRegionLevel.SIDO)
                assertEquals(4, calls)
                repository.findRegions(area, EmotionRegionLevel.SIGUNGU)
                assertEquals(4, calls)
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
                assertEquals(1, repository.findSnapshot(EmotionRegionLevel.SIGUNGU)?.size)
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
    fun `지역 API는 경계와 계층을 보내고 요약 Feature 별칭을 해석한다`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals("/api/v4/emotions/map/regions", request.url.encodedPath)
                            assertEquals("126.75", request.url.parameters["minLongitude"])
                            assertEquals("37.375", request.url.parameters["minLatitude"])
                            assertEquals("127.25", request.url.parameters["maxLongitude"])
                            assertEquals("37.625", request.url.parameters["maxLatitude"])
                            assertEquals("SIGUNGU", request.url.parameters["level"])
                            respond(
                                """[
                      {"type":"Feature","id":"11680","geometry":{"type":"Point","coordinates":[127.02,37.51]},"properties":{"regionName":"강남구","emotionCount":48,"representativeState":"FRUSTRATED"}}
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

    @Test
    fun `잘못된 Feature가 섞인 응답은 부분 성공이나 캐시로 취급하지 않는다`() =
        runTest {
            var requests = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requests++
                            respond(
                                """[
                                  {"type":"Feature","id":"11680","geometry":{"type":"Point","coordinates":[127.02,37.51]},"properties":{"name":"강남구","count":48}},
                                  {"type":"Feature","id":"bad","geometry":{"type":"Point","coordinates":[200,37.51]},"properties":{"name":"잘못된 지역","count":1}}
                                ]""",
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository = EmotionRegionMapRepositoryImpl(EmotionRegionMapApi(client.requests))
                val area = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)

                assertEquals(EmotionRegionResult.Failure, repository.findRegions(area, EmotionRegionLevel.SIGUNGU))
                assertEquals(null, repository.findSnapshot(EmotionRegionLevel.SIGUNGU))
                assertEquals(EmotionRegionResult.Failure, repository.findRegions(area, EmotionRegionLevel.SIGUNGU))
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }
}
