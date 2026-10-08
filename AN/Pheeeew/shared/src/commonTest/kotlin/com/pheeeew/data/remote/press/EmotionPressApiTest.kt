package com.pheeeew.data.remote.press

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.emotion.EmotionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionPressApiTest {
    @Test
    fun `loads my and all today summaries with relative day zero`() =
        runTest {
            val requests = mutableListOf<Pair<String, String?>>()
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requests += request.url.encodedPath to request.url.parameters["daysAgo"]
                            val body =
                                if (request.url.encodedPath.endsWith("/me")) {
                                    MY_TODAY_RESPONSE
                                } else {
                                    ALL_TODAY_RESPONSE
                                }
                            respond(body, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val api = EmotionPressApi(client.requests)
                val my = assertIs<ApiResult.Success<MyDailyPressResponseDto>>(api.findMyToday())
                val all = assertIs<ApiResult.Success<AllDailyPressResponseDto>>(api.findAllToday())

                assertEquals<List<Pair<String, String?>>>(
                    listOf(
                        "/api/v2/emotions/presses/me" to "0",
                        "/api/v2/emotions/presses/total" to "0",
                    ),
                    requests,
                )
                assertEquals("2026-10-08", my.value.pressDate)
                assertEquals(9L, my.value.total)
                assertEquals(0L, my.value.counts.getValue("DISCOURAGED"))
                assertEquals(82L, all.value.total)
            } finally {
                client.close()
            }
        }

    @Test
    fun `sends one accepted batch with coordinates and server emotion keys`() =
        runTest {
            var requestPath = ""
            var requestBody = ""
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            requestPath = request.url.encodedPath
                            requestBody = request.body.toByteArray().decodeToString()
                            respond(PRESS_RESPONSE, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("app-access-token") },
                )

            try {
                val response =
                    assertIs<ApiResult.Success<EmotionPressResponseDto>>(
                        EmotionPressApi(client.requests).submit(
                            location = CurrentLocation(37.5, 127.0, 10f, 1_791_416_400_000L),
                            counts = mapOf(EmotionState.FRUSTRATED to 2, EmotionState.ANGRY to 1),
                        ),
                    )

                assertEquals("/api/v2/emotions/presses", requestPath)
                assertEquals(
                    """{"latitude":37.5,"longitude":127.0,"counts":{"FRUSTRATED":2,"ANGRY":1}}""",
                    requestBody,
                )
                assertEquals("SEOUL", response.value.regionCode)
            } finally {
                client.close()
            }
        }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        const val MY_TODAY_RESPONSE =
            """{"pressDate":"2026-10-08","counts":{"FRUSTRATED":2,"IRRITATED":3,"EXHAUSTED":4,"DISCOURAGED":0,"ANGRY":0},"total":9}"""
        const val ALL_TODAY_RESPONSE = """{"pressDate":"2026-10-08","total":82}"""
        const val PRESS_RESPONSE =
            """{"regionCode":"SEOUL","counts":{"FRUSTRATED":2,"IRRITATED":0,"EXHAUSTED":0,"DISCOURAGED":0,"ANGRY":1},"total":3}"""
    }
}
