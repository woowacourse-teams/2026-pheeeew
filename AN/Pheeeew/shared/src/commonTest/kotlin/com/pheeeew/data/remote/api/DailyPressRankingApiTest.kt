package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.ranking.DailyPressRanking
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DailyPressRankingApiTest {
    @Test
    fun `일별 API에 날짜만 보내고 내 순위는 me로 식별한다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/api/v2/emotions/press-rankings/daily", request.url.encodedPath)
                        assertEquals(setOf("date"), request.url.parameters.names())
                        assertEquals("2026-10-26", request.url.parameters["date"])
                        assertEquals("Bearer auth", request.headers[HttpHeaders.Authorization])
                        respond(RESPONSE, HttpStatusCode.OK, JSON)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                val value =
                    assertIs<ApiResult.Success<DailyPressRanking>>(
                        DailyPressRankingApi(client.requests).find("2026-10-26"),
                    ).value
                assertEquals(listOf(1, 2, 2, 4), value.items.map { it.rank })
                assertEquals(103, value.me?.rank)
                assertEquals("익명", value.me?.nickname)
            } finally {
                client.close()
            }
        }

    @Test
    fun `미래 날짜와 빈 목록은 정상 데이터이며 날짜를 오늘로 바꾸지 않는다`() =
        runTest {
            val date = "2030-01-01"
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals(date, request.url.parameters["date"])
                        respond(
                            """
                            {"date":"2030-01-01","startAt":"2029-12-31T15:00:00Z",
                             "endAt":"2030-01-01T15:00:00Z","items":[],"me":null}
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            JSON,
                        )
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                val value =
                    assertIs<ApiResult.Success<DailyPressRanking>>(
                        DailyPressRankingApi(client.requests).find(date),
                    ).value
                assertEquals(date, value.date)
                assertEquals(emptyList(), value.items)
                assertNull(value.me)
            } finally {
                client.close()
            }
        }

    @Test
    fun `잘못된 날짜는 전송하지 않고 다른 날짜나 잘못된 일간 구간 응답은 실패한다`() =
        runTest {
            for (body in listOf(
                RESPONSE.replace("2026-10-26", "2026-10-27"),
                RESPONSE.replace("2026-10-25T15:00:00Z", "2026-10-25T00:00:00Z"),
                RESPONSE.replace("103", "0"),
            )) {
                var calls = 0
                val client =
                    createApiClient(
                        MockEngine {
                            calls++
                            respond(body, HttpStatusCode.OK, JSON)
                        },
                        ApiConfig("https://api.example.test"),
                        AccessTokenProvider { AccessToken("auth") },
                    )
                try {
                    val api = DailyPressRankingApi(client.requests)
                    assertFailsWith<IllegalArgumentException> { api.find("2026-02-29") }
                    assertEquals(0, calls)
                    assertIs<NetworkFailure.Contract>(assertIs<ApiResult.Failure>(api.find("2026-10-26")).reason)
                } finally {
                    client.close()
                }
            }
        }

    private companion object {
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
        val RESPONSE =
            """
            {"date":"2026-10-26","startAt":"2026-10-25T15:00:00Z","endAt":"2026-10-26T15:00:00Z",
             "items":[{"rank":1,"nickname":"익명","pressCount":200},
                      {"rank":2,"nickname":"익명","pressCount":150},
                      {"rank":2,"nickname":"공동","pressCount":150},
                      {"rank":4,"nickname":"다음","pressCount":100}],
             "me":{"rank":103,"nickname":"익명","pressCount":1}}
            """.trimIndent()
    }
}
