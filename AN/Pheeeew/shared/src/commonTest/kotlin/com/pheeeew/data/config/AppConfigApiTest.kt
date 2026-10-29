@file:Suppress("ktlint:standard:max-line-length")

package com.pheeeew.data.config

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.api.AppConfigApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AppConfigApiTest {
    @Test
    fun publicRequestAndStrictContract() =
        runTest {
            var body = """{"revision":1,"dailyPressRankingEnabled":false,"noticeMessage":"점검","updatedAt":"2026-10-29T03:00:00Z","future":true}"""
            var status = HttpStatusCode.OK
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals("/api/v2/app/config", request.url.encodedPath)
                            assertEquals("ios", request.url.parameters["platform"])
                            assertEquals(null, request.headers[HttpHeaders.Authorization])
                            respond(
                                body,
                                status,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider = null,
                )
            try {
                val api = AppConfigApi(client.requests, "ios")
                assertEquals(false, api.get().dailyPressRankingEnabled)
                for (invalid in listOf(
                    """{"revision":1,"noticeMessage":"","updatedAt":"2026-10-29T03:00:00Z"}""",
                    """{"revision":1,"dailyPressRankingEnabled":"false","noticeMessage":"","updatedAt":"2026-10-29T03:00:00Z"}""",
                    """{"revision":0,"dailyPressRankingEnabled":true,"noticeMessage":"","updatedAt":"bad"}""",
                    """{"revision":2,"dailyPressRankingEnabled":true,"noticeMessage":"${"😀".repeat(
                        201,
                    )}","updatedAt":"2026-10-29T03:00:00Z"}""",
                )) {
                    body = invalid
                    assertFails { api.get() }
                }
                for (error in listOf(
                    HttpStatusCode.BadRequest,
                    HttpStatusCode.NotFound,
                    HttpStatusCode.InternalServerError,
                )) {
                    status = error
                    body = "{}"
                    assertFails { api.get() }
                }
            } finally {
                client.close()
            }
        }
}
