@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.sigh.api

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import com.pheeeew.data.remote.sigh.dto.SighCreateV1RequestDto
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.sigh.SighBounds
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorSighV1ApiTest {
    @Test
    fun `한숨 조회 요청의 method path query를 검증한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v1/sighs", request.url.encodedPath)

                    assertEquals(
                        "127.1",
                        request.url.parameters["minLongitude"],
                    )
                    assertEquals(
                        "37.3",
                        request.url.parameters["minLatitude"],
                    )
                    assertEquals(
                        "127.2",
                        request.url.parameters["maxLongitude"],
                    )
                    assertEquals(
                        "37.4",
                        request.url.parameters["maxLatitude"],
                    )

                    respond(
                        content =
                            """
                            {
                                "type": "FeatureCollection",
                                "truncated": false,
                                "features": []
                            }
                            """.trimIndent(),
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
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )

            val api = KtorSighV1Api(client)

            val result =
                api.getSighs(
                    SighBounds(
                        minLongitude = 127.1,
                        minLatitude = 37.3,
                        maxLongitude = 127.2,
                        maxLatitude = 37.4,
                    ),
                )

            assertEquals("FeatureCollection", result.type)
            assertEquals(false, result.truncated)
            assertEquals(emptyList(), result.features)

            client.close()
        }

    @Test
    fun `한숨 등록 요청의 body를 검증한다`() =
        runTest {
            val requestId = "request-123"
            val request =
                SighCreateV1RequestDto(
                    requestId = requestId,
                    latitude = 37.5665,
                    longitude = 126.9780,
                )

            val engine =
                MockEngine { httpRequest: HttpRequestData ->
                    assertEquals(HttpMethod.Post, httpRequest.method)
                    assertEquals("/api/v1/sighs", httpRequest.url.encodedPath)

                    val body =
                        httpRequest.body
                            .toByteArray()
                            .decodeToString()

                    assertTrue(body.contains("\"requestId\":\"request-123\""))
                    assertTrue(body.contains("\"latitude\":37.5665"))
                    assertTrue(body.contains("\"longitude\":126.978"))

                    respond(
                        content =
                            """
                            {
                                "type": "Feature",
                                "id": 100,
                                "geometry": {
                                    "type": "Point",
                                    "coordinates": [126.9775, 37.5668]
                                },
                                "properties": {}
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                "application/geo+json",
                            ),
                    )
                }

            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )

            val api = KtorSighV1Api(client)
            val result = api.registerSigh(request)

            assertEquals(100L, result.id)
            assertEquals(
                listOf(126.9775, 37.5668),
                result.geometry.coordinates,
            )

            client.close()
        }

    @Test
    fun `같은 requestId로 재시도하면 201과 200 응답을 모두 처리한다`() =
        runTest {
            val request =
                SighCreateV1RequestDto(
                    requestId = "same-request-id",
                    latitude = 37.5665,
                    longitude = 126.978,
                )
            val responseJson =
                """
                {
                  "type": "Feature",
                  "id": 42,
                  "geometry": {
                    "type": "Point",
                    "coordinates": [126.978, 37.5665]
                  },
                  "properties": {}
                }
                """.trimIndent()
            var callCount = 0
            val responseStatuses = mutableListOf<HttpStatusCode>()

            val engine =
                MockEngine { httpRequest: HttpRequestData ->
                    callCount += 1

                    assertEquals(HttpMethod.Post, httpRequest.method)
                    assertEquals("/api/v1/sighs", httpRequest.url.encodedPath)

                    val body =
                        httpRequest.body
                            .toByteArray()
                            .decodeToString()
                    assertTrue(body.contains("\"requestId\":\"same-request-id\""))
                    assertTrue(body.contains("\"latitude\":37.5665"))
                    assertTrue(body.contains("\"longitude\":126.978"))

                    val status =
                        if (callCount == 1) {
                            HttpStatusCode.Created
                        } else {
                            HttpStatusCode.OK
                        }
                    responseStatuses += status

                    respond(
                        content = responseJson,
                        status = status,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                "application/geo+json",
                            ),
                    )
                }

            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighV1Api(client)

            val first = api.registerSigh(request)
            val second = api.registerSigh(request)

            assertEquals(first, second)
            assertEquals(2, callCount)
            assertEquals(
                listOf(HttpStatusCode.Created, HttpStatusCode.OK),
                responseStatuses,
            )

            client.close()
        }

    @Test
    fun `조회 400 응답을 InvalidRequest 예외로 변환한다`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}""",
                        status = HttpStatusCode.BadRequest,
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
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighV1Api(client)

            val exception =
                assertFailsWith<ApiException.InvalidRequest> {
                    api.getSighs(
                        SighBounds(
                            minLongitude = 127.1,
                            minLatitude = 37.3,
                            maxLongitude = 127.2,
                            maxLatitude = 37.4,
                        ),
                    )
                }

            assertEquals("COMMON-001", exception.code)
            assertEquals("요청 값이 올바르지 않습니다.", exception.message)
            client.close()
        }

    @Test
    fun `등록 500 응답을 Unknown 예외로 변환한다`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"code":"SIGH-500","message":"저장에 실패했습니다."}""",
                        status = HttpStatusCode.InternalServerError,
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
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighV1Api(client)

            val exception =
                assertFailsWith<ApiException.Unknown> {
                    api.registerSigh(
                        SighCreateV1RequestDto(
                            requestId = "request-500",
                            latitude = 37.5665,
                            longitude = 126.978,
                        ),
                    )
                }

            assertEquals("SIGH-500", exception.code)
            assertEquals("저장에 실패했습니다.", exception.message)
            client.close()
        }

    @Test
    fun `네트워크 오류를 Network 예외로 변환한다`() =
        runTest {
            val expected = IOException("연결 실패")
            val engine =
                MockEngine {
                    throw expected
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighV1Api(client)

            val exception =
                assertFailsWith<ApiException.Network> {
                    api.getSighs(
                        SighBounds(
                            minLongitude = 127.1,
                            minLatitude = 37.3,
                            maxLongitude = 127.2,
                            maxLatitude = 37.4,
                        ),
                    )
                }

            assertEquals("NETWORK_ERROR", exception.code)
            assertEquals("연결 실패", exception.message)
            client.close()
        }
}
