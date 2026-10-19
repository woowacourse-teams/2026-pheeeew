package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.dto.PressRankingResponseDto
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
                assertEquals("/api/v2/emotions/press-rankings", requestedPath)
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
                assertEquals("/api/v2/emotions/press-rankings/states/IRRITATED", requestedPath)
                assertEquals("2", requestedWeeksAgo)
                assertEquals("Bearer app-access-token", authorization)
                assertEquals("IRRITATED", result.value.state)
                assertEquals(
                    3_000_000_000L,
                    result.value.items
                        .single()
                        .pressCount,
                )
                assertEquals(150, result.value.me?.rank)
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
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    PressRankingApi(client.requests).find(521, null)
                }
            } finally {
                client.close()
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        const val EMPTY_RANKING =
            """{"weeksAgo":0,"startAt":"2026-09-28T00:00:00Z","endAt":"2026-10-05T00:00:00Z","items":[],"me":null}"""
        const val RANKING_WITH_MINE =
            """{"state":"IRRITATED","weeksAgo":2,"startAt":"2026-09-14T00:00:00Z","endAt":"2026-09-21T00:00:00Z","items":[{"rank":2,"nickname":"익명","pressCount":3000000000}],"me":{"rank":150,"nickname":"한숨많은곰","pressCount":31}}"""
    }
}
