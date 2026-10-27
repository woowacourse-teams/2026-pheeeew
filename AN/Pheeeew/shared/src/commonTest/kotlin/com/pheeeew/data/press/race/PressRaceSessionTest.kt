package com.pheeeew.data.press.race

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.data.press.race.transport.PressRaceClose
import com.pheeeew.data.press.race.transport.PressRaceConnection
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.PressRaceUpgradeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRaceSessionTest {
    @Test
    fun explicitRefreshOnlyUsesTheActiveSocketAndWaitsForNewBaseline() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.refresh(PressRaceView.ALL)
                runCurrent()
                assertTrue(transport.connections.isEmpty())
                session.setView(PressRaceView.ALL)
                runCurrent()
                val socket = transport.connections.single()
                session.refresh(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, socket.sent.size)
                socket.incoming.send(ready())
                socket.incoming.send(snapshot("1", PressRaceView.ALL))
                runCurrent()
                session.refresh(PressRaceView.PRESS)
                runCurrent()
                assertEquals(1, socket.sent.size)
                session.refresh(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, transport.connections.size)
                assertEquals("view.change", socket.command(1)["type"]?.jsonPrimitive?.content)
                assertTrue(session.state.value.isAwaitingSnapshot)
                assertNull(session.state.value.currentSnapshot)
                session.refresh(PressRaceView.ALL)
                runCurrent()
                assertEquals(2, socket.sent.size)
                socket.incoming.send(snapshot("2", PressRaceView.ALL))
                runCurrent()
                assertFalse(session.state.value.isAwaitingSnapshot)
                session.setView(null)
                runCurrent()
                session.refresh(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, transport.connections.size)
            } finally {
                session.close()
            }
        }

    @Test
    fun opensOnlyOnDemandAndBorrowsExistingTokenOnceForBothViews() =
        runTest {
            val transport = FakeTransport()
            val token = AccessToken("existing-token")
            var tokenCalls = 0
            val session =
                PressRaceSession(
                    transport,
                    AccessTokenProvider {
                        tokenCalls++
                        token
                    },
                    backgroundScope,
                )
            try {
                runCurrent()
                assertEquals(0, transport.connections.size)
                session.setView(PressRaceView.PRESS)
                session.setView(PressRaceView.PRESS)
                runCurrent()
                assertEquals(1, tokenCalls)
                assertSame(token, transport.token)
                val socket = transport.connections.single()
                assertEquals("session.init", socket.command(0)["type"]?.jsonPrimitive?.content)
                assertEquals("PRESS", socket.command(0)["view"]?.jsonPrimitive?.content)
                assertEquals("[]", socket.command(0)["pending"].toString())
                assertEquals(PressRacePhase.Initializing, session.state.value.phase)
                socket.incoming.send(ready())
                runCurrent()
                assertEquals(PressRacePhase.Ready, session.state.value.phase)
                assertEquals(1, socket.sent.size)
                assertNull(session.state.value.snapshot)
                assertTrue(session.state.value.isAwaitingSnapshot)
                assertNull(session.state.value.currentSnapshot)
                socket.incoming.send(snapshot("1", PressRaceView.PRESS))
                runCurrent()
                assertEquals(
                    "1",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                session.setView(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, transport.connections.size)
                assertEquals("view.change", socket.command(1)["type"]?.jsonPrimitive?.content)
                assertEquals("ALL", socket.command(1)["view"]?.jsonPrimitive?.content)
                socket.incoming.send(snapshot("2", PressRaceView.PRESS))
                runCurrent()
                assertEquals(
                    "1",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                socket.incoming.send(snapshot("3", PressRaceView.ALL))
                runCurrent()
                assertEquals(
                    PressRaceView.ALL,
                    session.state.value.snapshot
                        ?.view,
                )
                session.setView(PressRaceView.PRESS)
                runCurrent()
                assertEquals(1, transport.connections.size)
                assertEquals(1, tokenCalls)
            } finally {
                session.close()
            }
        }

    @Test
    fun viewChangedDuringInitializationIsSentOnlyAfterReady() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val socket = transport.connections.single()
                session.setView(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, socket.sent.size)
                socket.incoming.send(ready())
                runCurrent()
                assertEquals(2, socket.sent.size)
                assertEquals("ALL", socket.command(1)["view"]?.jsonPrimitive?.content)
                assertEquals(1, transport.connections.size)
            } finally {
                session.close()
            }
        }

    @Test
    fun inactiveClosesSocketButNotSharedClientAndReturnCreatesNewConnection() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val old = transport.connections.single()
                session.setView(null)
                runCurrent()
                assertEquals(1, old.closed)
                assertEquals(0, transport.closed)
                assertEquals(PressRacePhase.Disconnected, session.state.value.phase)
                session.setView(PressRaceView.ALL)
                runCurrent()
                assertEquals(2, transport.connections.size)
                assertEquals(
                    "ALL",
                    transport.connections
                        .last()
                        .command(0)["view"]
                        ?.jsonPrimitive
                        ?.content,
                )
            } finally {
                session.close()
            }
            assertEquals(1, transport.closed)
        }

    @Test
    fun upgradeAuthenticationFailureDoesNotRefreshOrRetryAutomatically() =
        runTest {
            for (status in listOf(401, 403)) {
                val transport = FakeTransport(PressRaceUpgradeException(status))
                var refreshCalls = 0
                val tokens =
                    object : RecoverableAccessTokenProvider {
                        override suspend fun accessToken() = AccessToken("secret-token")

                        override suspend fun recover(rejected: AccessToken): AccessToken {
                            refreshCalls++
                            error("must not refresh")
                        }
                    }
                val session = PressRaceSession(transport, tokens, backgroundScope)
                try {
                    session.setView(PressRaceView.PRESS)
                    runCurrent()
                    assertEquals(PressRacePhase.Failed, session.state.value.phase)
                    assertEquals(
                        PressRaceFailureKind.Authentication,
                        session.state.value.failure
                            ?.kind,
                    )
                    assertEquals(
                        status,
                        session.state.value.failure
                            ?.httpStatus,
                    )
                    assertEquals(1, transport.attempts)
                    assertEquals(0, refreshCalls)
                    assertFalse(
                        session.state.value
                            .toString()
                            .contains("secret-token"),
                    )
                } finally {
                    session.close()
                }
            }
        }

    @Test
    fun missingTokenFailsWithoutOpeningTransport() =
        runTest {
            val transport = FakeTransport()
            val session = PressRaceSession(transport, AccessTokenProvider { null }, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                assertEquals(
                    PressRaceFailureKind.Authentication,
                    session.state.value.failure
                        ?.kind,
                )
                assertEquals(0, transport.attempts)
            } finally {
                session.close()
            }
        }

    @Test
    fun serverAuthExpiryClosesConnectionAndPreservesLastSnapshot() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val socket = transport.connections.single()
                socket.incoming.send(ready())
                socket.incoming.send(snapshot("8", PressRaceView.PRESS))
                runCurrent()
                socket.finish(PressRaceClose(1008, "AUTH_EXPIRED"))
                runCurrent()
                assertEquals(PressRacePhase.Failed, session.state.value.phase)
                assertEquals(
                    PressRaceFailureKind.Authentication,
                    session.state.value.failure
                        ?.kind,
                )
                assertEquals(
                    "AUTH_EXPIRED",
                    session.state.value.failure
                        ?.code,
                )
                assertEquals(
                    "8",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                assertEquals(1, socket.closed)
                assertEquals(1, transport.attempts)
            } finally {
                session.close()
            }
        }

    @Test
    fun rejectsOldGenerationVersionsAndDatesWithoutRollingBackCurrentState() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val socket = transport.connections.single()
                socket.incoming.send(ready())
                socket.incoming.send(snapshot("9223372036854775808", PressRaceView.PRESS, date = "2026-10-27"))
                socket.incoming.send(snapshot("9223372036854775807", PressRaceView.PRESS))
                socket.incoming.send(snapshot("9223372036854775809", PressRaceView.PRESS, server = "old-server"))
                socket.incoming.send(snapshot("9223372036854775810", PressRaceView.PRESS, date = "2026-10-26"))
                runCurrent()
                assertEquals(
                    "9223372036854775808",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                assertEquals(
                    "2026-10-27",
                    session.state.value.snapshot
                        ?.date,
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun malformedOrPrematureSnapshotFailsAndReleasesConnection() =
        runTest {
            for (message in listOf("not-json", snapshot("1", PressRaceView.PRESS))) {
                val transport = FakeTransport()
                val session = session(transport, backgroundScope)
                try {
                    session.setView(PressRaceView.PRESS)
                    runCurrent()
                    transport.connections
                        .single()
                        .incoming
                        .send(message)
                    runCurrent()
                    assertEquals(
                        PressRaceFailureKind.Protocol,
                        session.state.value.failure
                            ?.kind,
                    )
                    assertEquals(1, transport.connections.single().closed)
                } finally {
                    session.close()
                }
            }
        }

    @Test
    fun ownerCloseIsTerminalIdempotentAndDoesNotCancelParentScope() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            session.close()
            session.close()
            session.setView(PressRaceView.ALL)
            runCurrent()
            assertEquals(PressRacePhase.Closed, session.state.value.phase)
            assertEquals(1, transport.closed)
            assertEquals(1, transport.attempts)
            assertNull(session.state.value.ready)
        }

    @Test
    fun cancellingInFlightOpenCannotCreateALateConnection() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val transport = FakeTransport(openGate = gate)
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                assertEquals(PressRacePhase.Connecting, session.state.value.phase)
                session.setView(null)
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertTrue(transport.connections.isEmpty())
                assertEquals(PressRacePhase.Disconnected, session.state.value.phase)
                session.setView(PressRaceView.ALL)
                runCurrent()
                assertEquals(1, transport.connections.size)
                assertEquals(
                    "ALL",
                    transport.connections
                        .single()
                        .command(0)["view"]
                        ?.jsonPrimitive
                        ?.content,
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun slowControlSendDoesNotPreventDeactivation() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val transport = FakeTransport(sendGate = gate)
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val socket = transport.connections.single()
                assertEquals(PressRacePhase.Initializing, session.state.value.phase)
                session.setView(null)
                runCurrent()
                assertEquals(PressRacePhase.Disconnected, session.state.value.phase)
                assertEquals(1, socket.closed)
                gate.complete(Unit)
                runCurrent()
                assertTrue(socket.sent.isEmpty())
            } finally {
                session.close()
            }
            assertTrue(backgroundScope.coroutineContext[Job]!!.isActive)
        }

    @Test
    fun viewChangeKeepsCachedSnapshotButWaitsForNewBaseline() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val socket = transport.connections.single()
                socket.incoming.send(ready())
                socket.incoming.send(snapshot("1", PressRaceView.PRESS))
                runCurrent()
                assertEquals(
                    "1",
                    session.state.value.currentSnapshot
                        ?.snapshotVersion,
                )
                session.setView(PressRaceView.ALL)
                runCurrent()
                assertTrue(session.state.value.isAwaitingSnapshot)
                assertNull(session.state.value.currentSnapshot)
                assertEquals(
                    "1",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                socket.incoming.send(
                    snapshot("2", PressRaceView.ALL, baseline = false),
                )
                runCurrent()
                assertNull(session.state.value.currentSnapshot)
                socket.incoming.send(snapshot("3", PressRaceView.ALL))
                runCurrent()
                assertFalse(session.state.value.isAwaitingSnapshot)
                assertEquals(
                    "3",
                    session.state.value.currentSnapshot
                        ?.snapshotVersion,
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun readyAfterReentryDoesNotExposeOldRankAsCurrentAndOldCloseCannotFailNewConnection() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            try {
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val old = transport.connections.single()
                old.incoming.send(ready())
                old.incoming.send(snapshot("10", PressRaceView.PRESS))
                runCurrent()
                session.setView(null)
                runCurrent()
                assertNull(session.state.value.currentSnapshot)
                session.setView(PressRaceView.PRESS)
                runCurrent()
                val current = transport.connections.last()
                current.incoming.send(ready())
                runCurrent()
                assertEquals(PressRacePhase.Ready, session.state.value.phase)
                assertEquals(
                    "10",
                    session.state.value.snapshot
                        ?.snapshotVersion,
                )
                assertTrue(session.state.value.isAwaitingSnapshot)
                assertNull(session.state.value.currentSnapshot)
                old.finish(PressRaceClose(1008, "AUTH_EXPIRED"))
                runCurrent()
                assertEquals(PressRacePhase.Ready, session.state.value.phase)
                assertNull(session.state.value.failure)
                current.incoming.send(snapshot("1", PressRaceView.PRESS))
                runCurrent()
                assertEquals(
                    "1",
                    session.state.value.currentSnapshot
                        ?.snapshotVersion,
                )
            } finally {
                session.close()
            }
        }

    @Test
    fun inputUsesSameWriterAndFiltersOldEnvelopeWithoutDroppingCurrentResult() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, backgroundScope)
            val results = mutableListOf<PressRaceInputResult>()
            val pending = PressRacePendingInput("original-id", "old-generation", mapOf("ANGRY" to 2))
            session.observeInputs(
                object : PressRaceInputObserver {
                    override suspend fun pending() = listOf(pending)

                    override suspend fun ready(value: PressRaceReady) = Unit

                    override suspend fun result(value: PressRaceInputResult) {
                        results += value
                    }
                },
            )
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val socket = transport.connections.single()
            assertTrue(socket.sent.single().contains("old-generation"))
            socket.incoming.send(ready())
            runCurrent()
            var attempts = 0
            backgroundScope.launch {
                session.sendInput(PressRaceProtocol.submit(pending), "c1") {
                    attempts++
                    true
                }
            }
            runCurrent()
            assertEquals(1, attempts)
            assertEquals(2, socket.sent.size)
            assertTrue(socket.sent.last().contains("old-generation"))
            for (generation in listOf("old", "c1")) {
                socket.incoming.send(
                    """{"v":1,"type":"input.result","serverGeneration":"s1",
                "connectionGeneration":"$generation","requestId":"original-id","status":"PENDING"}""",
                )
            }
            runCurrent()
            assertEquals(1, results.size)
            session.close()
        }

    @Test
    fun cancelledQueueDoesNotMarkNeverSentInputAsAttempted() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val transport = FakeTransport(sendGate = gate)
            val session = session(transport, backgroundScope)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.connections
                .single()
                .incoming
                .send(ready())
            runCurrent()
            var attempts = 0
            val completion = CompletableDeferred<Boolean>()
            backgroundScope.launch {
                completion.complete(
                    session.sendInput("input", "c1") {
                        attempts++
                        true
                    },
                )
            }
            runCurrent()
            session.setView(null)
            runCurrent()
            assertEquals(0, attempts)
            assertFalse(completion.await())
            gate.complete(Unit)
            runCurrent()
            assertTrue(
                transport.connections
                    .single()
                    .sent
                    .isEmpty(),
            )
            session.close()
        }

    private fun session(
        transport: FakeTransport,
        scope: CoroutineScope,
    ) = PressRaceSession(transport, AccessTokenProvider { AccessToken("existing-token") }, scope)
}

private class FakeTransport(
    private val failure: Exception? = null,
    private val openGate: CompletableDeferred<Unit>? = null,
    private val sendGate: CompletableDeferred<Unit>? = null,
) : PressRaceTransport {
    var attempts = 0
    var closed = 0
    var token: AccessToken? = null
    val connections = mutableListOf<FakeConnection>()

    override suspend fun connect(accessToken: AccessToken): PressRaceConnection {
        attempts++
        token = accessToken
        failure?.let { throw it }
        openGate?.await()
        return FakeConnection(sendGate).also { connections += it }
    }

    override fun close() {
        closed++
        connections.forEach { it.finish(null) }
    }
}

private class FakeConnection(
    private val sendGate: CompletableDeferred<Unit>? = null,
) : PressRaceConnection {
    val incoming = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var closed = 0
    private val reason = CompletableDeferred<PressRaceClose?>()

    override suspend fun send(text: String) {
        sendGate?.await()
        sent += text
    }

    override suspend fun receive(): String? = incoming.receiveCatching().getOrNull()

    override suspend fun awaitCloseReason() = reason.await()

    override suspend fun close() {
        closed++
        finish(null)
    }

    fun finish(close: PressRaceClose?) {
        reason.complete(close)
        incoming.close()
    }

    fun command(index: Int) = Json.parseToJsonElement(sent[index]).jsonObject
}

private fun ready() =
    """
    {"v":1,"type":"session.ready","serverGeneration":"s1","connectionGeneration":"c1",
    "date":"2026-10-26","counts":{"ANGRY":0},"results":[]}
    """.trimIndent()

private fun snapshot(
    version: String,
    view: PressRaceView,
    date: String = "2026-10-26",
    server: String = "s1",
    baseline: Boolean = true,
): String {
    val participant = PressRaceParticipant(null, null, 0, null, false)
    val data =
        PressRaceSnapshot(
            server,
            "c1",
            date,
            version,
            "2026-10-26T00:00:00Z",
            view,
            0,
            participant,
            emptyList(),
            emptyList(),
            baseline,
            PressRaceOvertakes(0, 0),
        )
    return JsonObject(
        Json.encodeToJsonElement(PressRaceSnapshot.serializer(), data).jsonObject +
            mapOf("v" to JsonPrimitive(1), "type" to JsonPrimitive("state.snapshot")),
    ).toString()
}
