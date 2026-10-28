package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressRaceTransportTest {
    @Test
    fun apiOriginBecomesSecureWebSocketWithoutChangingEnvironmentOrPort() {
        assertEquals(
            "wss://api-dev.pheeeew.com/api/v1/press-race",
            pressRaceUrl(ApiConfig("https://api-dev.pheeeew.com")).toString(),
        )
        assertEquals(
            "wss://api.pheeeew.com/api/v1/press-race",
            pressRaceUrl(ApiConfig("https://api.pheeeew.com/")).toString(),
        )
        assertEquals(
            "wss://api.test:8443/api/v1/press-race",
            pressRaceUrl(ApiConfig("https://api.test:8443")).toString(),
        )
    }

    @Test
    fun rejectsCredentialQueryFragmentAndBasePathInOrigin() {
        for (origin in listOf(
            "https://user:secret@api.test",
            "https://api.test?token=secret",
            "https://api.test/#x",
            "https://api.test/gateway",
        )) {
            assertFailsWith<IllegalArgumentException> { pressRaceUrl(ApiConfig(origin)) }
        }
    }

    @Test
    fun authenticatesAndExchangesTextWhilePreservingServerCloseReason() =
        runTest {
            withContext(Dispatchers.Default) {
                val socket = TestSocket(coroutineContext)
                val transport =
                    KtorPressRaceTransport(
                        MockEngine { request ->
                            assertEquals("wss", request.url.protocol.name)
                            assertEquals("/api/v1/press-race", request.url.encodedPath)
                            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                            assertTrue(request.url.parameters.isEmpty())
                            HttpResponseData(
                                HttpStatusCode.SwitchingProtocols,
                                GMTDate(),
                                headersOf(),
                                HttpProtocolVersion.HTTP_1_1,
                                socket,
                                currentCoroutineContext(),
                            )
                        },
                        ApiConfig("https://api.test"),
                    )
                try {
                    val connection = transport.connect(AccessToken("test-token"))
                    connection.send("{\"v\":1,\"type\":\"session.init\"}")
                    assertEquals(
                        "{\"v\":1,\"type\":\"session.init\"}",
                        (socket.outgoing.receive() as Frame.Text).readText(),
                    )
                    socket.incoming.send(Frame.Text("{\"type\":\"session.ready\"}"))
                    assertEquals("{\"type\":\"session.ready\"}", connection.receive())
                    socket.closeReason.complete(CloseReason(1008, "AUTH_EXPIRED"))
                    socket.incoming.close()
                    assertEquals(PressRaceClose(1008, "AUTH_EXPIRED"), connection.awaitCloseReason())
                    assertNull(connection.receive())
                } finally {
                    transport.close()
                    socket.dispose()
                }
            }
        }

    @Test
    fun enforcesUtf8CommandLimitWithoutTruncatingOrSendingOversizeText() =
        runTest {
            withContext(Dispatchers.Default) {
                val socket = TestSocket(coroutineContext)
                val transport = transport(socket)
                try {
                    val connection = transport.connect(AccessToken("test-token"))
                    connection.send("a".repeat(PRESS_RACE_MAX_COMMAND_BYTES))
                    assertEquals(
                        PRESS_RACE_MAX_COMMAND_BYTES,
                        socket.outgoing
                            .receive()
                            .data.size,
                    )
                    assertFailsWith<IllegalArgumentException> {
                        connection.send(
                            "a".repeat(PRESS_RACE_MAX_COMMAND_BYTES + 1),
                        )
                    }
                    assertFailsWith<IllegalArgumentException> { connection.send("가".repeat(6000)) }
                    assertTrue(socket.outgoing.tryReceive().isFailure)
                    // Incoming snapshots are not constrained by the server's 16KiB command limit.
                    val snapshot = "x".repeat(PRESS_RACE_MAX_COMMAND_BYTES + 1)
                    socket.incoming.send(Frame.Text(snapshot))
                    assertEquals(snapshot, connection.receive())
                    connection.close()
                    assertTrue(socket.closeReason.isCompleted)
                } finally {
                    transport.close()
                    socket.dispose()
                }
            }
        }

    @Test
    fun cancellingConnectDisposesPendingUpgradeAndClosedTransportCannotReconnect() =
        runTest {
            withContext(Dispatchers.Default) {
                val entered = CompletableDeferred<Unit>()
                val cancelled = CompletableDeferred<Unit>()
                val transport =
                    KtorPressRaceTransport(
                        MockEngine {
                            entered.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled.complete(Unit)
                            }
                        },
                        ApiConfig("https://api.test"),
                    )
                try {
                    val opening = async { transport.connect(AccessToken("test-token")) }
                    withTimeout(2_000) { entered.await() }
                    opening.cancel()
                    opening.join()
                    withTimeout(2_000) { cancelled.await() }
                    assertTrue(opening.isCancelled)
                    transport.close()
                    transport.close()
                    assertFailsWith<IllegalStateException> { transport.connect(AccessToken("test-token")) }
                } finally {
                    transport.close()
                }
            }
        }

    @Test
    fun failedUpgradeIsNotRetriedAutomatically() =
        runTest {
            withContext(Dispatchers.Default) {
                var attempts = 0
                val transport =
                    KtorPressRaceTransport(
                        MockEngine {
                            attempts++
                            respond("", HttpStatusCode.Unauthorized)
                        },
                        ApiConfig("https://api.test"),
                    )
                try {
                    val failure =
                        assertFailsWith<PressRaceUpgradeException> { transport.connect(AccessToken("test-token")) }
                    assertEquals(401, failure.statusCode)
                    assertEquals(1, attempts)
                } finally {
                    transport.close()
                }
            }
        }

    @Test
    fun redirectDoesNotForwardBearerToAnotherHost() =
        runTest {
            withContext(Dispatchers.Default) {
                var attempts = 0
                val transport =
                    KtorPressRaceTransport(
                        MockEngine {
                            attempts++
                            respond(
                                "",
                                HttpStatusCode.Found,
                                headersOf(HttpHeaders.Location, "https://another.test/socket"),
                            )
                        },
                        ApiConfig("https://api.test"),
                    )
                try {
                    val failure =
                        assertFailsWith<PressRaceUpgradeException> { transport.connect(AccessToken("test-token")) }
                    assertEquals(302, failure.statusCode)
                    assertEquals(1, attempts)
                } finally {
                    transport.close()
                }
            }
        }

    @Test
    fun binaryAndOversizeIncomingMessagesEndConnectionInsteadOfBecomingJson() =
        runTest {
            withContext(Dispatchers.Default) {
                for (frame in listOf(
                    Frame.Binary(true, byteArrayOf(1)),
                    Frame.Text(
                        "x".repeat(PRESS_RACE_MAX_RECEIVED_BYTES + 1),
                    ),
                )) {
                    val socket = TestSocket(coroutineContext)
                    val transport = transport(socket)
                    try {
                        val connection = transport.connect(AccessToken("test-token"))
                        socket.incoming.send(frame)
                        assertFailsWith<IllegalStateException> { connection.receive() }
                        assertTrue(socket.closeReason.isCompleted)
                        assertFailsWith<IllegalStateException> { connection.send("{}") }
                    } finally {
                        transport.close()
                        socket.dispose()
                    }
                }
            }
        }

    @Test
    fun closingTransportCancelsAnOpeningConnectionWithoutWaitingForConnectTimeout() =
        runTest {
            withContext(Dispatchers.Default) {
                val entered = CompletableDeferred<Unit>()
                val transport =
                    KtorPressRaceTransport(
                        MockEngine {
                            entered.complete(Unit)
                            awaitCancellation()
                        },
                        ApiConfig("https://api.test"),
                    )
                try {
                    val opening = async { transport.connect(AccessToken("test-token")) }
                    withTimeout(2_000) { entered.await() }
                    transport.close()
                    withTimeout(2_000) { opening.join() }
                    assertTrue(opening.isCancelled)
                } finally {
                    transport.close()
                }
            }
        }

    private fun transport(socket: TestSocket) =
        KtorPressRaceTransport(
            MockEngine {
                HttpResponseData(
                    HttpStatusCode.SwitchingProtocols,
                    GMTDate(),
                    headersOf(),
                    HttpProtocolVersion.HTTP_1_1,
                    socket,
                    currentCoroutineContext(),
                )
            },
            ApiConfig("https://api.test"),
        )
}

private class TestSocket(
    parent: CoroutineContext,
) : DefaultWebSocketSession {
    override val coroutineContext = parent + Job(parent[Job])
    override var masking = true
    override var maxFrameSize = Long.MAX_VALUE
    override var pingIntervalMillis = 0L
    override var timeoutMillis = 0L
    override val incoming = Channel<Frame>(Channel.UNLIMITED)
    override val outgoing = Channel<Frame>(Channel.UNLIMITED)
    override val closeReason = CompletableDeferred<CloseReason?>()
    override val extensions = emptyList<WebSocketExtension<*>>()

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) = Unit

    override suspend fun send(frame: Frame) {
        if (frame is Frame.Close) {
            closeReason.complete(frame.readReason())
            incoming.close()
        } else {
            outgoing.send(frame)
        }
    }

    override suspend fun flush() = Unit

    @Deprecated("Use cancel instead", level = DeprecationLevel.ERROR)
    override fun terminate() = dispose()

    fun dispose() {
        closeReason.complete(null)
        incoming.cancel()
        outgoing.cancel()
        coroutineContext.cancel()
    }
}
