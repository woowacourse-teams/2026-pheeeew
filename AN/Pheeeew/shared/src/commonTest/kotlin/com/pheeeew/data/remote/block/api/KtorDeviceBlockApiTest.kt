@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.block.api

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createHttpClient
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.remote.block.dto.DeviceBlockCreateRequestDto
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

class KtorDeviceBlockApiTest {
    @Test
    fun `사용자 차단 요청은 인증 헤더와 한숨 ID를 전달한다`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v2/blocks/devices", request.url.encodedPath)
                    assertEquals("Bearer access-123", request.headers[HttpHeaders.Authorization])
                    assertEquals(ContentType.Application.Json, request.body.contentType)

                    val body = request.body.toByteArray().decodeToString()
                    assertEquals("{\"sighId\":42}", body)
                    respond(
                        content = BLOCK_RESPONSE,
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client =
                createHttpClient(
                    engine = engine,
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                )
            val api = KtorDeviceBlockApi(client, TestAccessTokenStore(AccessToken("access-123")))

            val result = api.create(DeviceBlockCreateRequestDto(sighId = 42L))

            assertEquals(7L, result.blockId)
            assertEquals(42L, result.sighId)
            client.close()
        }

    private companion object {
        const val BLOCK_RESPONSE =
            """
            {
              "blockId": 7,
              "sighId": 42,
              "nickname": "날아가는 고라니",
              "memo": null,
              "createdAt": "2026-09-15T00:00:00Z"
            }
            """
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
