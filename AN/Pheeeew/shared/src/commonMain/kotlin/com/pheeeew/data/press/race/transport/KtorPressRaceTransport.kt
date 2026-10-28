package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal const val PRESS_RACE_MAX_COMMAND_BYTES = 16 * 1024

// This is a client safety ceiling, not the BE command limit: top100 snapshots can exceed 16KiB.
internal const val PRESS_RACE_MAX_RECEIVED_BYTES = 1024 * 1024

/** Separate from ApiClient: no HTTP request/read timeout, retry, logging or token refresh plugin. */
internal class KtorPressRaceTransport(
    private val engine: HttpClientEngine,
    config: ApiConfig,
) : PressRaceTransport {
    private val endpoints = PressRaceEndpoint.entries.associateWith { pressRaceUrl(config, it) }
    private val connectTimeoutMillis = config.timeouts.connectMillis
    private val client =
        HttpClient(engine) {
            followRedirects = false // Never forward the Bearer Upgrade to another destination.
            install(WebSockets)
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status != HttpStatusCode.SwitchingProtocols) {
                        throw PressRaceUpgradeException(response.status.value)
                    }
                }
            }
        }
    private val work = SupervisorJob(client.coroutineContext[Job])
    private val scope = CoroutineScope(client.coroutineContext + work)

    override suspend fun connect(
        accessToken: AccessToken,
        endpoint: PressRaceEndpoint,
    ): PressRaceConnection {
        check(work.isActive) { "Press race transport is closed" }
        val lifetime = Job(work)
        val opened = CompletableDeferred<PressRaceConnection>(lifetime)
        CoroutineScope(scope.coroutineContext + lifetime).launch {
            try {
                client.webSocket(request = {
                    url.takeFrom(endpoints.getValue(endpoint))
                    header(HttpHeaders.Authorization, "Bearer ${accessToken.value}")
                }) {
                    opened.complete(KtorPressRaceConnection(this, lifetime))
                    closeReason.await()
                }
            } catch (failure: Throwable) {
                opened.completeExceptionally(failure)
            } finally {
                lifetime.complete()
            }
        }
        return try {
            withTimeout(connectTimeoutMillis) { opened.await() }
        } catch (failure: Throwable) {
            lifetime.cancel()
            throw failure
        }
    }

    override fun close() {
        work.cancel()
        client.coroutineContext.cancel()
        client.close()
        engine.close()
    }
}

internal class KtorPressRaceConnection(
    private val session: DefaultClientWebSocketSession,
    private val lifetime: Job,
) : PressRaceConnection {
    override suspend fun send(text: String) {
        check(lifetime.isActive) { "Press race connection is closed" }
        val frame = Frame.Text(text)
        require(frame.data.size <= PRESS_RACE_MAX_COMMAND_BYTES) { "Press race command exceeds 16KiB" }
        session.send(frame)
    }

    override suspend fun receive(): String? {
        while (true) {
            val result = session.incoming.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            val frame = result.getOrNull() ?: return null
            when (frame) {
                is Frame.Text -> {
                    if (frame.data.size > PRESS_RACE_MAX_RECEIVED_BYTES) {
                        close()
                        error("Press race message exceeds client receive limit")
                    }
                    return frame.readText()
                }

                is Frame.Binary -> {
                    close()
                    error("Press race requires text messages")
                }

                // Default Ktor/native engines handle Ping/Pong and message reassembly.
                is Frame.Close -> {
                    return null
                }

                else -> {
                    continue
                }
            }
        }
    }

    override suspend fun awaitCloseReason(): PressRaceClose? =
        session.closeReason.await()?.let { PressRaceClose(it.code, it.message) }

    override suspend fun close() {
        try {
            withTimeoutOrNull(1_000L) {
                session.close(CloseReason(CloseReason.Codes.NORMAL, ""))
                session.closeReason.await()
            }
        } finally {
            lifetime.cancel()
        }
    }
}
