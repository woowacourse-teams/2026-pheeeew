@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientFactoryTest {
    @Serializable
    data class HealthResponse(
        val ok: Boolean,
    )

    @Test
    fun `MockEngine으로 URL과 JSON 응답을 검증한다`() {
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(
                        "https://api-dev.pheeeew.com/ping",
                        request.url.toString(),
                    )

                    respond(
                        content = """{"ok":true}""",
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config =
                        ApiConfig(
                            baseUrl = "https://api-dev.pheeeew.com",
                        ),
                )
            val response: HealthResponse =
                client.get("/ping").body()

            assertEquals(true, response.ok)
            client.close()
        }
    }
}
