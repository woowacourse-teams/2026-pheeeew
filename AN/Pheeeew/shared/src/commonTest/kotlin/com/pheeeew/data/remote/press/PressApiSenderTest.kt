package com.pheeeew.data.remote.press

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private typealias MockResponseHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

class PressApiSenderTest {
    @Test
    fun `replays the same batch once after recoverable authentication failure`() =
        runTest {
            var requests = 0
            val bodies = mutableListOf<String>()
            val provider = RecoverableProvider()
            val client =
                client(
                    handler = { request ->
                        requests++
                        bodies += request.body.toByteArray().decodeToString()
                        assertEquals(
                            if (requests == 1) "Bearer old-token" else "Bearer new-token",
                            request.headers[HttpHeaders.Authorization],
                        )
                        if (requests == 1) {
                            respond(
                                """{"code":"AUTH-001"}""",
                                HttpStatusCode.Unauthorized,
                                jsonHeaders(),
                            )
                        } else {
                            respond(VALID_RESPONSE, headers = jsonHeaders())
                        }
                    },
                    provider = provider,
                )
            try {
                val result = PressApiSender(EmotionPressApi(client.requests)).send(batch())

                assertEquals(2, requests)
                assertEquals(1, provider.recoveries)
                assertEquals(bodies.first(), bodies.last())
                val accepted = assertIs<PressSendResult.Accepted>(result)
                assertEquals(6L, accepted.today.total)
                assertEquals(2L, accepted.today.counts.getValue(EmotionState.ANGRY))
            } finally {
                client.close()
            }
        }

    @Test
    fun `maps valid POST response to accepted daily personal totals without idempotency support`() =
        runTest {
            val client = client(handler = { respond(VALID_RESPONSE, headers = jsonHeaders()) })
            try {
                val sender = PressApiSender(EmotionPressApi(client.requests))
                val result = sender.send(batch()) as PressSendResult.Accepted

                assertEquals(6L, result.today.total)
                assertEquals(2L, result.today.counts.getValue(EmotionState.ANGRY))
                assertEquals(0L, result.today.counts.getValue(EmotionState.FRUSTRATED))
                assertEquals(null, sender.idempotencyWindowMillis)
            } finally {
                client.close()
            }
        }

    @Test
    fun `treats malformed successful write response as unknown`() =
        runTest {
            val client = client(handler = { respond("{}", headers = jsonHeaders()) })
            try {
                val sender = PressApiSender(EmotionPressApi(client.requests))
                assertEquals(PressSendResult.OutcomeUnknown, sender.send(batch()))
            } finally {
                client.close()
            }
        }

    @Test
    fun `maps validation rejection to rejected and auth failure to safely retryable`() =
        runTest {
            val badRequestClient = client(handler = { respond("{}", HttpStatusCode.BadRequest, jsonHeaders()) })
            val unauthorizedClient = client(handler = { respond("{}", HttpStatusCode.Unauthorized, jsonHeaders()) })
            try {
                assertEquals(
                    PressSendResult.Rejected,
                    PressApiSender(EmotionPressApi(badRequestClient.requests)).send(batch()),
                )
                assertEquals(
                    PressSendResult.NotSent,
                    PressApiSender(EmotionPressApi(unauthorizedClient.requests)).send(batch()),
                )
            } finally {
                badRequestClient.close()
                unauthorizedClient.close()
            }
        }

    @Test
    fun `treats server errors as unknown writes`() =
        runTest {
            val client = client(handler = { respond("{}", HttpStatusCode.InternalServerError, jsonHeaders()) })
            try {
                assertEquals(
                    PressSendResult.OutcomeUnknown,
                    PressApiSender(EmotionPressApi(client.requests)).send(batch()),
                )
            } finally {
                client.close()
            }
        }

    private fun client(
        handler: MockResponseHandler,
        provider: AccessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
    ) = createApiClient(
        engine = MockEngine(handler),
        config = ApiConfig("https://api.test"),
        accessTokenProvider = provider,
    )

    private fun batch() =
        PressBatch(
            sequence = 7L,
            counts = linkedMapOf(EmotionState.ANGRY to 2, EmotionState.EXHAUSTED to 1),
        )

    private fun jsonHeaders() = headersOf("Content-Type", ContentType.Application.Json.toString())

    private class RecoverableProvider : RecoverableAccessTokenProvider {
        var recoveries = 0

        override suspend fun accessToken() = AccessToken("old-token")

        override suspend fun recover(rejected: AccessToken): AccessToken {
            recoveries++
            return AccessToken("new-token")
        }
    }

    private companion object {
        const val VALID_RESPONSE =
            """{"counts":{"FRUSTRATED":0,"IRRITATED":0,"EXHAUSTED":1,"DISCOURAGED":3,"ANGRY":2},"total":6}"""
    }
}
