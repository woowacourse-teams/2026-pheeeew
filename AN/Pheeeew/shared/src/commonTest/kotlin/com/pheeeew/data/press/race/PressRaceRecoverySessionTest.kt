package com.pheeeew.data.press.race

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.core.network.SessionFailureDetails
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.press.race.transport.PressRaceClose
import com.pheeeew.data.press.race.transport.PressRaceConnection
import com.pheeeew.data.press.race.transport.PressRaceEndpoint
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.PressRaceUpgradeException
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.DeviceSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRaceRecoverySessionTest {
    private fun TestScope.session(
        transport: RecoveryTransport,
        tokens: RecoveryTokens = RecoveryTokens(),
    ) = PressRaceSession(
        transport,
        tokens,
        backgroundScope,
        PressRaceReconnectPolicy({ 0.0 }),
        noticeClock = { testScheduler.currentTime },
    )

    @Test
    fun failureBlocksInputAndNoticeBeforeSlowSocketCloseFinishes() =
        runTest {
            val transport = RecoveryTransport().apply { closeGate = CompletableDeferred() }
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val socket = transport.sockets.single()
            socket.incoming.send(baseline(results = "[]"))
            socket.incoming.send(rank())
            socket.incoming.send(rank(version = "2", baseline = false).replace("\"passed\":0", "\"passed\":1"))
            runCurrent()
            assertTrue(session.state.value.notice != null)
            socket.finish(null)
            runCurrent()
            assertTrue(socket.closed)
            assertEquals(PressRacePhase.Failed, session.state.value.phase)
            assertEquals(null, session.state.value.ready)
            assertEquals(null, session.state.value.notice)
            assertTrue(session.state.value.isRecovering)
            transport.closeGate!!.complete(Unit)
            runCurrent()
            session.close()
        }

    @Test
    fun latestPositiveSurvivesZeroConflationAndOldExpiryCannotClearReplacement() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val socket = transport.sockets.single()
            socket.incoming.send(baseline(results = "[]"))
            socket.incoming.send(rank())
            runCurrent()

            fun crossing(version: String) =
                rank(version = version, baseline = false)
                    .replace("\"passed\":0,\"passedBy\":0", "\"passed\":2,\"passedBy\":3")
            socket.incoming.send(crossing("2"))
            socket.incoming.send(rank(version = "3", baseline = false))
            runCurrent()
            val first = session.state.value.notice!!
            assertEquals(2L, first.passed)
            assertEquals(3L, first.passedBy)
            assertEquals(
                "3",
                session.state.value.snapshot
                    ?.snapshotVersion,
            )
            advanceTimeBy(1_999)
            runCurrent()
            assertEquals(first, session.state.value.notice)
            socket.incoming.send(crossing("4"))
            runCurrent()
            val second = session.state.value.notice!!
            assertTrue(second.id > first.id)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(second, session.state.value.notice)
            advanceTimeBy(1_999)
            runCurrent()
            assertEquals(null, session.state.value.notice)
            session.close()
        }

    @Test
    fun alertClearsOnViewRefreshAndBackgroundAndDoesNotSurviveReconnection() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val socket = transport.sockets.single()
            socket.incoming.send(baseline(results = "[]"))
            socket.incoming.send(rank())
            socket.incoming.send(rank(version = "2", baseline = false).replace("\"passed\":0", "\"passed\":1"))
            runCurrent()
            assertTrue(session.state.value.notice != null)
            session.refresh(PressRaceView.PRESS)
            runCurrent()
            assertEquals(null, session.state.value.notice)
            socket.incoming.send(rank(version = "3"))
            socket.incoming.send(rank(version = "4", baseline = false).replace("\"passed\":0", "\"passed\":1"))
            runCurrent()
            assertTrue(session.state.value.notice != null)
            session.setView(PressRaceView.ALL)
            runCurrent()
            assertEquals(null, session.state.value.notice)
            session.setView(null)
            runCurrent()
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val current = transport.sockets.last()
            current.incoming.send(baseline(results = "[]"))
            current.incoming.send(rank())
            runCurrent()
            assertEquals(null, session.state.value.notice)
            session.close()
        }

    @Test
    fun lostAckReconnectionSettlesOriginalInputWithoutRepeatingTheOptimisticCount() =
        runTest {
            val transport = RecoveryTransport()
            val events = mutableListOf<PressRaceDiagnostic>()
            val session =
                PressRaceSession(
                    transport,
                    RecoveryTokens(),
                    backgroundScope,
                    PressRaceReconnectPolicy({ 0.0 }),
                    PressRaceDiagnostics { event, _ -> events += event },
                )
            val input =
                PressRaceInputCoordinator(session, backgroundScope, { testScheduler.currentTime }, { "local-input" })
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val first = transport.sockets.single()
            first.incoming.send(baseline(results = "[]"))
            first.incoming.send(rank())
            runCurrent()
            repeat(100) { input.accept(com.pheeeew.domain.model.emotion.EmotionState.ANGRY) }
            runCurrent()
            assertTrue(first.sent.last().contains("local-input"))
            assertEquals(100L, input.state.value.pendingPressCount)
            first.finish(null)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            val next = transport.sockets.last()
            assertTrue(next.sent.single().contains("local-input"))
            assertTrue(next.sent.single().contains("\"connectionGeneration\":\"c\""))
            val results = """[{"requestId":"local-input","status":"APPLIED",
                "appliedDate":"2026-10-27","appliedCounts":{"ANGRY":100}}]"""
            next.incoming.send(
                baseline(results = results)
                    .replace("\"connectionGeneration\":\"c\"", "\"connectionGeneration\":\"c2\"")
                    .replace("\"counts\":{}", "\"counts\":{\"ANGRY\":100}"),
            )
            next.incoming.send(rank().replace("\"connectionGeneration\":\"c\"", "\"connectionGeneration\":\"c2\""))
            runCurrent()
            assertEquals(
                100L,
                input.state.value.myToday
                    ?.total,
            )
            assertEquals(0L, input.state.value.pendingPressCount)
            assertEquals(
                0L,
                input.state.value.optimisticCounts.values
                    .sum(),
            )
            assertEquals(2, events.count { it == PressRaceDiagnostic.ReadyReceived })
            assertEquals(2, events.count { it == PressRaceDiagnostic.BaselineReceived })
            assertFalse(next.sent.any { it.contains("press.submit") })
            session.close()
        }

    @Test
    fun httpAndWebSocketShareTheDeviceManagersSingleRefreshFlight() =
        runTest {
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            if (calls > 1) gate.await()
                            return DeviceSessionResult.Ready(
                                DeviceAccess(
                                    if (calls ==
                                        1
                                    ) {
                                        "A"
                                    } else {
                                        "B"
                                    },
                                    1_000,
                                    100_000,
                                    1,
                                ),
                            )
                        }
                    },
                    backgroundScope,
                ) { 1_000 }
            val old = manager.accessToken()
            val transport = RecoveryTransport()
            val session = PressRaceSession(transport, manager, backgroundScope, PressRaceReconnectPolicy({ 0.0 }))
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val http = async { manager.recover(old) }
            runCurrent()
            transport.sockets.single().finish(PressRaceClose(1008, "AUTH_EXPIRED"))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(2, calls)
            gate.complete(Unit)
            runCurrent()
            assertEquals("B", http.await().value)
            assertEquals("B", transport.tokens.last().value)
            assertEquals(2, calls)
            session.close()
        }

    @Test
    fun freshBaselineResetsBackoffButAnotherServerVersionIsNotComparedNumerically() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val first = transport.sockets.single()
            first.incoming.send(baseline(results = "[]"))
            first.incoming.send(rank(version = "999999999999999999999999"))
            runCurrent()
            first.finish(null)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            val next = transport.sockets.last()
            next.incoming.send(baseline(results = "[]").replace("\"s\"", "\"new-server\""))
            next.incoming.send(rank(server = "new-server", version = "1"))
            runCurrent()
            assertEquals(
                "1",
                session.state.value.currentSnapshot
                    ?.snapshotVersion,
            )
            assertFalse(session.state.value.isRecovering)
            next.finish(null)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(3, transport.sockets.size)
            session.close()
        }

    @Test
    fun onlyNewDateBaselineStartsOneCountResynchronizationAndOldReadyCannotRollItBack() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            val first = transport.sockets.single()
            first.incoming.send(baseline(results = "[]"))
            first.incoming.send(rank())
            runCurrent()
            first.incoming.send(rank(date = "2026-10-28", version = "2", baseline = false))
            runCurrent()
            assertEquals(1, transport.sockets.size)
            first.incoming.send(rank(date = "2026-10-28", version = "3"))
            runCurrent()
            assertEquals(2, transport.sockets.size)
            assertEquals("2026-10-28", session.state.value.dateFloor)
            assertEquals(null, session.state.value.currentSnapshot)
            transport.sockets
                .last()
                .incoming
                .send(baseline(results = "[]"))
            runCurrent()
            assertEquals(PressRacePhase.Failed, session.state.value.phase)
            session.refresh(PressRaceView.PRESS)
            runCurrent()
            val current = transport.sockets.last()
            current.incoming.send(baseline(results = "[]").replace("2026-10-27", "2026-10-28"))
            current.incoming.send(rank(date = "2026-10-28"))
            runCurrent()
            assertEquals(
                "2026-10-28",
                session.state.value.currentSnapshot
                    ?.date,
            )
            session.close()
        }

    @Test fun backoffIsBoundedAndNeverResetsJustBecauseUpgradeWorked() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.sockets.last().finish(null)
            runCurrent()
            advanceTimeBy(499)
            runCurrent()
            assertEquals(1, transport.sockets.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, transport.sockets.size)
            transport.sockets.last().finish(null)
            runCurrent()
            advanceTimeBy(999)
            runCurrent()
            assertEquals(2, transport.sockets.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(3, transport.sockets.size)
            session.close()
            val policy = PressRaceReconnectPolicy({ 1.0 })
            assertEquals(listOf(600L, 1_200L, 2_400L, 4_800L, 5_000L, 5_000L), (1..6).map(policy::delayMillis))
        }

    @Test fun tokenExpiryRecoversTheRejectedTokenOnceAndDoesNotRefreshForever() =
        runTest {
            val transport = RecoveryTransport()
            val tokens = RecoveryTokens()
            val session = session(transport, tokens)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.sockets.last().finish(PressRaceClose(1008, "AUTH_EXPIRED"))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, tokens.recoveries)
            assertEquals("original", tokens.rejected?.value)
            assertEquals("renewed", transport.tokens.last().value)
            transport.sockets.last().finish(PressRaceClose(1008, "AUTH_EXPIRED"))
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, tokens.recoveries)
            assertEquals(2, transport.sockets.size)
            assertEquals(PressRacePhase.Failed, session.state.value.phase)
            session.close()
        }

    @Test fun forbiddenDoesNotRefreshAndUpgradeUnauthorizedUsesOneRecovery() =
        runTest {
            for (status in listOf(401, 403)) {
                val transport = RecoveryTransport().apply { failure = PressRaceUpgradeException(status) }
                val tokens = RecoveryTokens()
                val session = session(transport, tokens)
                session.setView(PressRaceView.PRESS)
                runCurrent()
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(if (status == 401) 1 else 0, tokens.recoveries)
                assertEquals(if (status == 401) 2 else 1, transport.endpoints.size)
                session.close()
            }
        }

    @Test fun leavingDuringTokenRecoveryCannotOpenLateSocket() =
        runTest {
            val transport = RecoveryTransport()
            val tokens = RecoveryTokens().apply { gate = CompletableDeferred() }
            val session = session(transport, tokens)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.sockets.last().finish(PressRaceClose(1008, "AUTH_EXPIRED"))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, tokens.recoveries)
            session.setView(null)
            runCurrent()
            tokens.gate!!.complete(Unit)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, transport.sockets.size)
            assertEquals(PressRacePhase.Disconnected, session.state.value.phase)
            session.close()
        }

    @Test fun initWatchdogAndBackgroundCancelHaveNoDuplicateConnections() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(
                "INIT_TIMEOUT",
                session.state.value.failure
                    ?.code,
            )
            session.setView(PressRaceView.ALL)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(2, transport.sockets.size)
            assertTrue(
                transport.sockets
                    .last()
                    .sent
                    .single()
                    .contains("ALL"),
            )
            session.setView(null)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, transport.sockets.size)
            session.close()
        }

    @Test fun changedIdentityNeverReplaysPendingInputs() =
        runTest {
            val transport = RecoveryTransport()
            val tokens = RecoveryTokens().apply { renewed = AccessToken("new-device", 2) }
            val session = session(transport, tokens)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.sockets.last().finish(PressRaceClose(1008, "AUTH_EXPIRED"))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(
                "SESSION_CHANGED",
                session.state.value.failure
                    ?.code,
            )
            session.refresh(PressRaceView.PRESS)
            session.setView(null)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, transport.sockets.size)
            session.close()
        }

    @Test fun recoveryOnlySettlesButNeverBecomesReadyOrRequestsRanks() =
        runTest {
            val transport = RecoveryTransport()
            val session = session(transport)
            val observer = RecoveryObserver()
            session.observeInputs(observer)
            session.setView(PressRaceView.PRESS)
            runCurrent()
            transport.sockets
                .last()
                .incoming
                .send(error("RACE_DISABLED"))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listOf(PressRaceEndpoint.Competition, PressRaceEndpoint.RecoveryOnly), transport.endpoints)
            assertTrue(transport.sockets.first().closed)
            val socket = transport.sockets.last()
            socket.incoming.send(baseline("session.recovered"))
            runCurrent()
            assertEquals(1, observer.baselines.size)
            assertEquals(PressRacePhase.Settled, session.state.value.phase)
            assertEquals(1, socket.sent.size)
            assertTrue(socket.closed)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, transport.sockets.size)
            session.close()
        }

    @Test fun missingOrNonfinalRecoveryResultDoesNotPublishReady() =
        runTest {
            for (results in listOf("[]", """[{"requestId":"pending","status":"PENDING"}]""")) {
                val transport = RecoveryTransport()
                val session = session(transport)
                val observer = RecoveryObserver()
                session.observeInputs(observer)
                session.setView(PressRaceView.PRESS)
                runCurrent()
                transport.sockets
                    .last()
                    .incoming
                    .send(baseline(results = results))
                runCurrent()
                assertEquals(PressRacePhase.Failed, session.state.value.phase)
                assertTrue(observer.baselines.isEmpty())
                session.close()
            }
        }
}

private class RecoveryTokens : RecoverableAccessTokenProvider {
    var recoveries = 0
    var rejected: AccessToken? = null
    var gate: CompletableDeferred<Unit>? = null
    var renewed = AccessToken("renewed", 1)

    override suspend fun accessToken() = AccessToken("original", 1)

    override suspend fun recover(rejected: AccessToken): AccessToken {
        recoveries++
        this.rejected = rejected
        gate?.await()
        return renewed
    }
}

private class RecoveryTransport : PressRaceTransport {
    var closeGate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null
    val sockets = mutableListOf<RecoverySocket>()
    val endpoints = mutableListOf<PressRaceEndpoint>()
    val tokens = mutableListOf<AccessToken>()

    override suspend fun connect(
        accessToken: AccessToken,
        endpoint: PressRaceEndpoint,
    ): PressRaceConnection {
        endpoints += endpoint
        tokens += accessToken
        failure?.let { throw it }
        return RecoverySocket(closeGate).also { sockets += it }
    }

    override fun close() {
        sockets.forEach { it.finish(null) }
    }
}

private class RecoverySocket(
    private val closeGate: CompletableDeferred<Unit>? = null,
) : PressRaceConnection {
    val incoming = Channel<String>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    private val reason = CompletableDeferred<PressRaceClose?>()
    var closed = false

    override suspend fun send(text: String) {
        sent += text
    }

    override suspend fun receive() = incoming.receiveCatching().getOrNull()

    override suspend fun awaitCloseReason() = reason.await()

    override suspend fun close() {
        closed = true
        closeGate?.await()
        finish(null)
    }

    fun finish(close: PressRaceClose?) {
        reason.complete(close)
        incoming.close()
    }
}

private class RecoveryObserver : PressRaceInputObserver {
    val baselines = mutableListOf<PressRaceReady>()

    override suspend fun pending() = listOf(PressRacePendingInput("pending", "old", mapOf("ANGRY" to 1)))

    override suspend fun ready(value: PressRaceReady) {
        baselines += value
    }

    override suspend fun result(value: PressRaceInputResult) = Unit
}

private fun error(code: String) =
    """{"v":1,"type":"protocol.error","serverGeneration":"s","connectionGeneration":"c","code":"$code"}"""

private fun baseline(
    type: String = "session.ready",
    results: String = """[{"requestId":"pending","status":"REJECTED"}]""",
) = """{"v":1,"type":"$type","serverGeneration":"s","connectionGeneration":"c",
        "date":"2026-10-27","counts":{},"results":$results,"inputEnabled":false}"""

private fun rank(
    date: String = "2026-10-27",
    version: String = "1",
    baseline: Boolean = true,
    server: String = "s",
): String =
    """{"v":1,"type":"state.snapshot","serverGeneration":"$server","connectionGeneration":"c",
        "date":"$date","snapshotVersion":"$version","generatedAt":"2026-10-27T00:00:00Z",
        "view":"PRESS","activeCount":0,"me":{"publicId":null,"nickname":null,"score":0,"rank":null,"participating":false},
        "nearby":[],"top100":[],"baseline":$baseline,"overtakes":{"passed":0,"passedBy":0}}"""
