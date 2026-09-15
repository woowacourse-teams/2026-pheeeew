@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.report.api

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.domain.model.device.AccessToken
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorSighReportApiTest {
    @Test
    fun `신고 요청은 한숨과 기기 식별자 및 사유를 body로 전달한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("Bearer access-123", request.headers[HttpHeaders.Authorization])
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v2/reports", request.url.encodedPath)
                    assertEquals(ContentType.Application.Json, request.body.contentType)

                    val body = request.body.toByteArray().decodeToString()
                    assertTrue(body.contains("\"sighId\":42"))
                    assertTrue(body.contains("\"deviceId\":\"5d1ad34e-1e20-4f20-a20e-3825a095fe6b\""))
                    assertTrue(body.contains("\"reason\":\"광고성 게시물입니다\""))

                    respond(
                        content = REPORT_RESPONSE,
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighReportApi(client, TestAccessTokenStore(AccessToken("access-123")))

            val result =
                api.create(
                    SighReportCreateRequestDto(
                        sighId = 42L,
                        deviceId = "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                        reason = "광고성 게시물입니다",
                    ),
                )

            assertEquals(7L, result.report.id)
            assertTrue(result.isNew)
            assertEquals(42L, result.report.sighId)
            assertEquals("광고성 게시물입니다", result.report.reason)
            assertEquals("2026-09-01T02:44:00Z", result.report.createdAt)
            client.close()
        }

    @Test
    fun `이미 신고한 한숨은 200 응답으로 중복 신고로 구분한다`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = REPORT_RESPONSE,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorSighReportApi(client)

            val result =
                api.create(
                    SighReportCreateRequestDto(
                        sighId = 42L,
                        deviceId = "device-id",
                        reason = "광고성 게시물입니다",
                    ),
                )

            assertEquals(7L, result.report.id)
            assertEquals(false, result.isNew)
            client.close()
        }

    @Test
    fun `access token 만료 시 refresh 후 신고 요청을 재시도한다`() =
        runTest {
            val store = TestAccessTokenStore(AccessToken("expired"))
            var requestCount = 0
            var refreshCount = 0
            val engine =
                MockEngine { request ->
                    requestCount++
                    if (requestCount == 1) {
                        assertEquals("Bearer expired", request.headers[HttpHeaders.Authorization])
                        respond(
                            content = """{"code":"AUTH-001","message":"expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        assertEquals("Bearer refreshed", request.headers[HttpHeaders.Authorization])
                        respond(
                            content = REPORT_RESPONSE,
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api =
                KtorSighReportApi(
                    client = client,
                    accessTokenStore = store,
                    refreshAccessToken = {
                        refreshCount++
                        AccessToken("refreshed")
                    },
                )

            api.create(
                SighReportCreateRequestDto(
                    sighId = 42L,
                    deviceId = "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                    reason = "광고성 게시물입니다",
                ),
            )

            assertEquals(2, requestCount)
            assertEquals(1, refreshCount)
            client.close()
        }

    private companion object {
        val REPORT_RESPONSE =
            """
            {
              "id": 7,
              "sighId": 42,
              "reason": "광고성 게시물입니다",
              "createdAt": "2026-09-01T02:44:00Z"
            }
            """.trimIndent()
    }

    private class TestAccessTokenStore(
        override var accessToken: AccessToken?,
    ) : AccessTokenStore {
        override fun save(accessToken: AccessToken) {
            this.accessToken = accessToken
        }

        override fun clear() {
            accessToken = null
        }
    }
}
