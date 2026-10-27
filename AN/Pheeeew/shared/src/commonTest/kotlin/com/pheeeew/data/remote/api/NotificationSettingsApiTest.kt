package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPreferences
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
import kotlin.test.assertFailsWith

class NotificationSettingsApiTest {
    @Test
    fun `컬렉션 변경은 서버의 순위 OFF를 유지하고 저장 성공 뒤 재조회하지 않는다`() =
        runTest {
            val methods = mutableListOf<HttpMethod>()
            val client =
                createApiClient(
                    MockEngine { request ->
                        methods += request.method
                        assertEquals("Bearer auth", request.headers[HttpHeaders.Authorization])
                        assertEquals("/api/v3/devices/me/notification-settings", request.url.encodedPath)
                        if (request.method == HttpMethod.Put) {
                            assertEquals(
                                "{\"collectionClaimableEnabled\":true,\"rankChangedEnabled\":false}",
                                (request.body as TextContent).text,
                            )
                            respond("", HttpStatusCode.NoContent)
                        } else {
                            respond(
                                "{\"collectionClaimableEnabled\":true,\"rankChangedEnabled\":false}",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                assertEquals(
                    NotificationPreferences(true, false),
                    NotificationSettingsApi(client.requests).update(NotificationKind.COLLECTION, true),
                )
                assertEquals(listOf(HttpMethod.Get, HttpMethod.Put), methods)
                methods.clear()
                assertEquals(
                    NotificationPreferences(true, false),
                    NotificationSettingsApi(client.requests).replace(NotificationPreferences(true, false)),
                )
                assertEquals(listOf(HttpMethod.Put), methods)
            } finally {
                client.close()
            }
        }

    @Test
    fun `조회 실패는 기본 ON이나 저장으로 이어지지 않는다`() =
        runTest {
            var calls = 0
            val client =
                createApiClient(
                    MockEngine { request ->
                        calls++
                        assertEquals(HttpMethod.Get, request.method)
                        respond("", HttpStatusCode.InternalServerError)
                    },
                    ApiConfig("https://api.example.test"),
                    AccessTokenProvider { AccessToken("auth") },
                )
            try {
                assertFailsWith<IllegalStateException> {
                    NotificationSettingsApi(client.requests).update(NotificationKind.COLLECTION, true)
                }
                assertEquals(1, calls)
            } finally {
                client.close()
            }
        }
}
