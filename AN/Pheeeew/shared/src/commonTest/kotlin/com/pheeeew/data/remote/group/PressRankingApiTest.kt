package com.pheeeew.data.remote.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PressRankingApiTest {
    @Test
    fun `전체 랭킹은 weeksAgo와 인증 토큰으로 요청한다`() =
        runTest {
            var requestedPath = ""
            var requestedWeeksAgo: String? = null
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            requestedWeeksAgo = request.url.parameters["weeksAgo"]
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("Bearer app-access-token", request.headers[HttpHeaders.Authorization])
                            respond(EMPTY_RANKING, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                assertIs<ApiResult.Success<PressRankingResponseDto>>(PressRankingApi(client.requests).find(3, null))
                assertEquals("/api/v2/groups/press-rankings", requestedPath)
                assertEquals("3", requestedWeeksAgo)
            } finally {
                client.close()
            }
        }

    @Test
    fun `감정 랭킹은 state path와 weeksAgo로 요청한다`() =
        runTest {
            var requestedPath = ""
            var requestedWeeksAgo: String? = null
            var authorization: String? = null
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestedPath = request.url.encodedPath
                            requestedWeeksAgo = request.url.parameters["weeksAgo"]
                            authorization = request.headers[HttpHeaders.Authorization]
                            assertEquals(setOf("weeksAgo"), request.url.parameters.names())
                            respond(RANKING_WITH_MINE, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val result =
                    assertIs<ApiResult.Success<PressRankingResponseDto>>(
                        PressRankingApi(client.requests).find(2, "IRRITATED"),
                    )
                assertEquals("/api/v2/groups/press-rankings/states/IRRITATED", requestedPath)
                assertEquals("2", requestedWeeksAgo)
                assertEquals("Bearer app-access-token", authorization)
                assertEquals("IRRITATED", result.value.state)
                assertTrue(
                    result.value.items
                        .single()
                        .mine,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `알 수 없는 상태와 음수 주차는 요청하지 않는다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { error("invalid arguments must not make a request") },
                    ApiConfig("https://api.test"),
                    null,
                )
            try {
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    PressRankingApi(client.requests).find(0, "SAD")
                }
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    PressRankingApi(client.requests).find(-1, null)
                }
            } finally {
                client.close()
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        const val EMPTY_RANKING =
            """{"weeksAgo":0,"startAt":"2026-09-28T00:00:00Z","endAt":"2026-10-05T00:00:00Z","hasPrevious":true,"items":[]}"""
        const val RANKING_WITH_MINE =
            """{"state":"IRRITATED","weeksAgo":2,"startAt":"2026-09-14T00:00:00Z","endAt":"2026-09-21T00:00:00Z","hasPrevious":true,"items":[{"rank":2,"groupId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","name":"히유 클럽","score":31,"mine":true}]}"""
    }
}
