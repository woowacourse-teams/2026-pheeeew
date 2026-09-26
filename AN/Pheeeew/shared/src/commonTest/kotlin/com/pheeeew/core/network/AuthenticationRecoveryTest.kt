package com.pheeeew.core.network

import com.pheeeew.data.remote.device.DeviceAttestationDto
import com.pheeeew.data.remote.device.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.KtorDeviceSessionApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthenticationRecoveryTest {
    @Test
    fun `auth failure refreshes and retries exactly once`() =
        runTest {
            var calls = 0
            val provider = Provider()
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(if (calls == 1) "Bearer old" else "Bearer new", request.headers["Authorization"])
                        respond(
                            """{"code":"AUTH-001"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                val failure = assertIs<ApiResult.Failure>(client.requests.execute(read()) { it.bodyAsText() })
                assertEquals("AUTH-001", assertIs<NetworkFailure.HttpStatus>(failure.reason).error?.code)
                assertEquals(2, calls)
                assertEquals(1, provider.recoveries)
            } finally {
                client.close()
            }
        }

    @Test
    fun `nonreplayable write and forbidden responses never refresh`() =
        runTest {
            for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
                val provider = Provider()
                val client =
                    createApiClient(
                        MockEngine {
                            respond("""{"code":"AUTH-001"}""", status, headersOf("Content-Type", "application/json"))
                        },
                        ApiConfig("https://example.com"),
                        provider,
                    )
                try {
                    client.requests.execute(ApiRequest(HttpMethod.Post, "/test", RequestKind.WRITE)) { it.bodyAsText() }
                    assertEquals(0, provider.recoveries)
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun `session identity change is returned without replay`() =
        runTest {
            var calls = 0
            val provider =
                object : RecoverableAccessTokenProvider {
                    override suspend fun accessToken() = AccessToken("old", 1)

                    override suspend fun recover(rejected: AccessToken): AccessToken =
                        throw SessionAccessException(SessionFailureDetails("SESSION_CHANGED"))
                }
            val client =
                createApiClient(
                    MockEngine {
                        calls++
                        respond(
                            """{"code":"AUTH-001"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                    ApiConfig("https://example.com"),
                    provider,
                )
            try {
                val failure = assertIs<ApiResult.Failure>(client.requests.execute(read()) { it.bodyAsText() })
                assertEquals(
                    "SESSION_CHANGED",
                    assertIs<NetworkFailure.SessionProviderFailed>(failure.reason).details?.reason,
                )
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `device registration accepts 200 and 201 without consulting provider`() =
        runTest {
            for (status in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
                val client =
                    createApiClient(
                        MockEngine { request ->
                            assertEquals(null, request.headers["Authorization"])
                            respond(
                                """{"accessToken":"a","refreshToken":"r","expiresIn":1800}""",
                                status,
                                headersOf("Content-Type", "application/json"),
                            )
                        },
                        ApiConfig("https://example.com"),
                        AccessTokenProvider { error("must not request a token") },
                    )
                try {
                    assertIs<ApiResult.Success<*>>(
                        KtorDeviceSessionApi(
                            client.requests,
                        ).register(DeviceRegistrationRequestDto("request", DeviceAttestationDto("ANDROID"))),
                    )
                } finally {
                    client.close()
                }
            }
        }

    private fun read() = ApiRequest(HttpMethod.Get, "/test", RequestKind.READ)

    private class Provider : RecoverableAccessTokenProvider {
        var recoveries = 0

        override suspend fun accessToken() = AccessToken("old")

        override suspend fun recover(rejected: AccessToken): AccessToken {
            recoveries++
            return AccessToken("new")
        }
    }
}
