@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.version

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppVersionApiTest {
    @Test
    fun `인증 없이 플랫폼별 버전 정책을 조회한다`() =
        runTest {
            val client =
                createHttpClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("/api/v2/app/version", request.url.encodedPath)
                            assertEquals("android", request.url.parameters["platform"])
                            assertNull(request.headers[HttpHeaders.Authorization])
                            respond(
                                content =
                                    """{"minSupportedVersion":"1.2.0","latestVersion":"1.10.0","storeUrl":"https://example.com/app"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            try {
                val result = AppVersionApi(client, "android").getPolicy()
                assertEquals("1.2.0", result.minSupportedVersion)
                assertEquals("1.10.0", result.latestVersion)
                assertEquals("https://example.com/app", result.storeUrl)
            } finally {
                client.close()
            }
        }
}
