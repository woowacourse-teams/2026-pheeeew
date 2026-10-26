package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.createApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PushTokenApiTest {
    @Test
    fun `PUT은 기존 인증과 원문 token 필드만 보내고 204를 성공 처리한다`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("/api/v3/devices/me/push-token", request.url.encodedPath)
                        assertEquals("Bearer device-auth", request.headers[HttpHeaders.Authorization])
                        assertEquals("{\"token\":\" sdk-token \"}", (request.body as TextContent).text)
                        respond("", HttpStatusCode.NoContent)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("device-auth") },
                )
            try {
                assertEquals(ApiResult.Success(Unit), PushTokenApi(client.requests).register(" sdk-token "))
            } finally {
                client.close()
            }
        }

    @Test
    fun `DELETE는 수신 설정 경로를 호출하지 않고 같은 기기 인증으로 204를 처리한다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(HttpMethod.Delete, request.method)
                        assertEquals("/api/v3/devices/me/push-token", request.url.encodedPath)
                        assertEquals("Bearer auth", request.headers[HttpHeaders.Authorization])
                        respond("", HttpStatusCode.NoContent)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                val api = PushTokenApi(client.requests)
                assertEquals(ApiResult.Success(Unit), api.unregister())
                assertEquals(ApiResult.Success(Unit), api.unregister())
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `AUTH-001은 기존 인증 복구 후 멱등 PUT을 한번 재전송한다`() =
        runTest {
            var calls = 0
            var recoveries = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("expired", 1)

                    override suspend fun recover(rejected: AccessToken): AccessToken {
                        recoveries++
                        return AccessToken("renewed", 1)
                    }
                }
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        if (calls == 1) {
                            respond(
                                "{\"code\":\"AUTH-001\"}",
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        } else {
                            assertEquals("Bearer renewed", request.headers[HttpHeaders.Authorization])
                            respond("", HttpStatusCode.NoContent)
                        }
                    },
                    ApiConfig("https://api.example.test"),
                    provider,
                )
            try {
                assertEquals(ApiResult.Success(Unit), PushTokenApi(client.requests).register("token"))
                assertEquals(2, calls)
                assertEquals(1, recoveries)
            } finally {
                client.close()
            }
        }
}
