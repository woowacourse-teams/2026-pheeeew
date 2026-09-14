@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.report.api

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
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
            val api = KtorSighReportApi(client)

            val result =
                api.create(
                    SighReportCreateRequestDto(
                        sighId = 42L,
                        deviceId = "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                        reason = "광고성 게시물입니다",
                    ),
                )

            assertEquals(7L, result.id)
            assertEquals(42L, result.sighId)
            assertEquals("광고성 게시물입니다", result.reason)
            assertEquals("2026-09-01T02:44:00Z", result.createdAt)
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
}
