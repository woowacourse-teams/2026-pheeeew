package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.createApiClient
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotificationSettingsSessionTest {
    @Test
    fun `조회 검증 직후 기기가 바뀌면 저장 전에 중단한다`() =
        runTest {
            var generation = 1L
            var calls = 0
            var switchAfterRead = true
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(HttpMethod.Get, request.method)
                        respond(SETTINGS, HttpStatusCode.OK, JSON)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth", generation) },
                )
            val api =
                NotificationSettingsApi(client.requests) {
                    val snapshot = generation
                    if (calls == 1 && switchAfterRead) {
                        switchAfterRead = false
                        generation = 2L
                    }
                    snapshot
                }
            try {
                assertFailsWith<IllegalStateException> { api.update(NotificationKind.COLLECTION, true) }
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun `PUT 인증 토큰을 기다리는 중 세대가 바뀌면 다른 기기에 전송하지 않는다`() =
        runTest {
            var generation = 1L
            var credentials = 0
            val methods = mutableListOf<HttpMethod>()
            val client =
                createApiClient(
                    MockEngine { request ->
                        methods += request.method
                        respond(SETTINGS, HttpStatusCode.OK, JSON)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider {
                        if (++credentials == 2) generation = 2L
                        AccessToken("device-$generation", generation)
                    },
                )
            try {
                val api = NotificationSettingsApi(client.requests) { generation }
                assertFailsWith<IllegalStateException> { api.update(NotificationKind.COLLECTION, true) }
                assertEquals(listOf(HttpMethod.Get), methods)
            } finally {
                client.close()
            }
        }

    @Test
    fun `인증 복구 후 동일 세대만 PUT을 재전송한다`() =
        runTest {
            listOf(false, true).forEach { changeDevice ->
                var generation = 1L
                var puts = 0
                val provider =
                    object : RecoverableAccessTokenProvider {
                        override suspend fun accessToken() = AccessToken("device-$generation", generation)

                        override suspend fun recover(rejected: AccessToken): AccessToken {
                            if (changeDevice) generation = 2L
                            return AccessToken("renewed", generation)
                        }
                    }
                val client =
                    createApiClient(
                        MockEngine { request ->
                            if (request.method == HttpMethod.Put) {
                                puts++
                                if (puts == 1) {
                                    respond("{\"code\":\"AUTH-001\"}", HttpStatusCode.Unauthorized, JSON)
                                } else {
                                    assertEquals("Bearer renewed", request.headers[HttpHeaders.Authorization])
                                    respond("", HttpStatusCode.NoContent)
                                }
                            } else {
                                respond(SETTINGS, HttpStatusCode.OK, JSON)
                            }
                        },
                        ApiConfig("https://api.example.test"),
                        provider,
                    )
                try {
                    val api = NotificationSettingsApi(client.requests) { generation }
                    if (changeDevice) {
                        assertFailsWith<IllegalStateException> { api.update(NotificationKind.COLLECTION, true) }
                        assertEquals(1, puts)
                    } else {
                        assertEquals(
                            NotificationPreferences(true, false),
                            api.update(NotificationKind.COLLECTION, true),
                        )
                        assertEquals(2, puts)
                    }
                } finally {
                    client.close()
                }
            }
        }

    private companion object {
        const val SETTINGS = "{\"collectionClaimableEnabled\":true,\"rankChangedEnabled\":false}"
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
