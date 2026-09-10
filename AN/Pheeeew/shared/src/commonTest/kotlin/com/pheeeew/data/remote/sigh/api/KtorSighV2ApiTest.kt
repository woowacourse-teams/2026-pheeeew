@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.sigh.api

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.domain.model.sigh.SighBounds
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorSighV2ApiTest {
    @Test
    fun `첫 페이지는 지도 영역만 전달한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v2/sighs", request.url.encodedPath)
                    assertEquals("126.9", request.url.parameters["minLongitude"])
                    assertEquals("37.5", request.url.parameters["minLatitude"])
                    assertEquals("127.1", request.url.parameters["maxLongitude"])
                    assertEquals("37.6", request.url.parameters["maxLatitude"])
                    assertNull(request.url.parameters["cursor"])

                    respondJson(PAGE_RESPONSE)
                }
            val client = createClient(engine)
            val api = KtorSighV2Api(client)

            val result =
                api.getFirstPage(
                    SighBounds(
                        minLongitude = 126.9,
                        minLatitude = 37.5,
                        maxLongitude = 127.1,
                        maxLatitude = 37.6,
                    ),
                )

            assertEquals(1, result.items.size)
            assertTrue(result.hasNext)
            assertEquals("opaque-cursor", result.nextCursor)
            client.close()
        }

    @Test
    fun `다음 페이지는 커서만 전달한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v2/sighs", request.url.encodedPath)
                    assertEquals("opaque-cursor", request.url.parameters["cursor"])
                    assertNull(request.url.parameters["minLongitude"])
                    assertNull(request.url.parameters["minLatitude"])
                    assertNull(request.url.parameters["maxLongitude"])
                    assertNull(request.url.parameters["maxLatitude"])

                    respondJson(EMPTY_PAGE_RESPONSE)
                }
            val client = createClient(engine)
            val api = KtorSighV2Api(client)

            val result = api.getNextPage("opaque-cursor")

            assertTrue(result.items.isEmpty())
            assertFalse(result.hasNext)
            assertNull(result.nextCursor)
            client.close()
        }

    @Test
    fun `상세 조회는 한숨 식별자를 경로에 전달한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v2/sighs/42", request.url.encodedPath)

                    respondJson(FEATURE_RESPONSE)
                }
            val client = createClient(engine)
            val api = KtorSighV2Api(client)

            val result = api.getById(42)

            assertEquals(42, result.id)
            assertEquals("오늘은 조금 지쳤다", result.properties.memo)
            assertEquals("날아가는 고라니", result.properties.nickname)
            client.close()
        }

    @Test
    fun `등록 요청은 메모를 포함한 body를 전달한다`() =
        runTest {
            val engine =
                MockEngine { request: HttpRequestData ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v2/sighs", request.url.encodedPath)
                    assertEquals(ContentType.Application.Json, request.body.contentType)

                    val body = request.body.toByteArray().decodeToString()
                    assertTrue(body.contains("\"requestId\":\"request-123\""))
                    assertTrue(body.contains("\"latitude\":37.5665"))
                    assertTrue(body.contains("\"longitude\":126.978"))
                    assertTrue(body.contains("\"memo\":\"오늘은 조금 지쳤다\""))

                    respondJson(FEATURE_RESPONSE, HttpStatusCode.Created)
                }
            val client = createClient(engine)
            val api = KtorSighV2Api(client)

            val result =
                api.create(
                    SighCreateV2RequestDto(
                        requestId = "request-123",
                        latitude = 37.5665,
                        longitude = 126.978,
                        memo = "오늘은 조금 지쳤다",
                    ),
                )

            assertEquals(42, result.id)
            client.close()
        }

    private fun createClient(engine: MockEngine) =
        createHttpClient(
            engine = engine,
            config = ApiConfig("https://api-dev.pheeeew.com"),
        )

    private companion object {
        val FEATURE_RESPONSE =
            """
            {
              "type": "Feature",
              "id": 42,
              "geometry": {
                "type": "Point",
                "coordinates": [126.9774, 37.5669]
              },
              "properties": {
                "createdAt": "2026-09-01T12:00:00Z",
                "memo": "오늘은 조금 지쳤다",
                "nickname": "날아가는 고라니"
              }
            }
            """.trimIndent()

        val PAGE_RESPONSE =
            """
            {
              "items": [$FEATURE_RESPONSE],
              "hasNext": true,
              "nextCursor": "opaque-cursor"
            }
            """.trimIndent()

        val EMPTY_PAGE_RESPONSE =
            """
            {
              "items": [],
              "hasNext": false,
              "nextCursor": null
            }
            """.trimIndent()
    }
}

private fun MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
