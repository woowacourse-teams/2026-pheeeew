package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
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

class CollectionTodayApiTest {
    @Test
    fun `알림 진입은 오늘 진행도만 조회하고 카드를 자동 수령하지 않는다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/api/v1/collection-cards/today", request.url.encodedPath)
                        assertEquals("Bearer auth", request.headers[HttpHeaders.Authorization])
                        respond(
                            """
                            {
                              "date":"2026-10-26",
                              "stamps":{"count":2,"target":1,"remaining":0},
                              "presses":{"count":110,"target":100,"remaining":0},
                              "claimed":false,"claimable":true,"items":[]
                            }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                val today =
                    assertIs<ApiResult.Success<CollectionToday>>(
                        CollectionTodayApi(client.requests).find(),
                    ).value
                assertEquals("2026-10-26", today.date)
                assertEquals(true, today.claimable)
                assertEquals(110L, today.presses.count)
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }
}
