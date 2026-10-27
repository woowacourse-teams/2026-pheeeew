package com.pheeeew.data.press.race

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressAcceptance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRaceInputCoordinatorTest {
    private suspend fun TestScope.input(port: InputPort): PressRaceInputCoordinator {
        port.clock = { testScheduler.currentTime }
        var id = 0
        val coordinator =
            PressRaceInputCoordinator(port, backgroundScope, { testScheduler.currentTime }, { "id-${++id}" })
        runCurrent()
        port.observer.ready(port.ready)
        port.state.value =
            PressRaceSessionState(phase = PressRacePhase.Ready, view = PressRaceView.PRESS, ready = port.ready)
        runCurrent()
        return coordinator
    }

    @Test fun fixedBatchDeadlineDoesNotMoveWithRepeatedTapsAndRetryReusesBytes() =
        runTest {
            val port = InputPort()
            val input = input(port)
            assertEquals(PressAcceptance.Accepted, input.accept(EmotionState.ANGRY))
            advanceTimeBy(199)
            input.accept(EmotionState.ANGRY)
            assertTrue(port.sent.isEmpty())
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, port.sent.size)
            assertTrue(
                port.sent
                    .single()
                    .second
                    .contains("\"ANGRY\":2"),
            )
            advanceTimeBy(250)
            runCurrent()
            assertEquals(2, port.sent.size)
            assertEquals(port.sent[0].second, port.sent[1].second)
            assertEquals(2L, input.state.value.pendingPressCount)
        }

    @Test fun exactTenSecondsSwitchesToBatchedStatusAndTwentyStopsWithoutRejecting() =
        runTest {
            val port = InputPort()
            val input = input(port)
            repeat(100) { input.accept(EmotionState.ANGRY) }
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(10_000L, port.sent.last().first)
            assertTrue(
                port.sent
                    .last()
                    .second
                    .contains("press.status"),
            )
            val submits = port.sent.count { it.second.contains("press.submit") }
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(submits, port.sent.count { it.second.contains("press.submit") })
            assertEquals(
                (10_000L..19_000L step 1_000).toList(),
                port.sent
                    .filter {
                        it.second.contains("press.status")
                    }.map { it.first },
            )
            assertTrue(input.state.value.isOutcomeUnknown)
            assertEquals(listOf("connection"), port.recoveryRequests)
            assertEquals(PressAcceptance.Unavailable, input.accept(EmotionState.ANGRY))
            val before = port.sent.size
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(before, port.sent.size)
            assertEquals(1, port.recoveryRequests.size)
            assertEquals(100L, input.state.value.pendingPressCount)
        }

    @Test fun firstHundredFreezesAndCapacityIncludesUnsentReservation() =
        runTest {
            val port = InputPort()
            val input = input(port)
            repeat(2_000) { assertEquals(PressAcceptance.Accepted, input.accept(EmotionState.ANGRY)) }
            assertEquals(PressAcceptance.NumericLimit, input.accept(EmotionState.ANGRY))
            runCurrent()
            assertEquals(20, port.sent.size)
            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(
                port.sent
                    .last()
                    .second
                    .contains("press.status"),
            )
            assertEquals(20, port.observer.pending().size)
        }

    @Test fun leavingCancelsNeverSentButPreservesAttemptedAndReadySettlesAtomically() =
        runTest {
            val port = InputPort()
            val input = input(port)
            repeat(100) { input.accept(EmotionState.ANGRY) }
            runCurrent()
            input.accept(EmotionState.EXHAUSTED)
            port.state.value = PressRaceSessionState()
            runCurrent()
            assertEquals(100L, input.state.value.pendingPressCount)
            assertEquals(1, port.observer.pending().size)
            assertEquals(PressAcceptance.Unavailable, input.accept(EmotionState.ANGRY))
            port.observer.ready(
                port.ready.copy(
                    counts = mapOf("ANGRY" to 100),
                    results =
                        listOf(
                            PressRaceInputResult("id-1", "APPLIED", "2026-10-27", mapOf("ANGRY" to 100)),
                        ),
                ),
            )
            assertEquals(
                100L,
                input.state.value.myToday
                    ?.total,
            )
            assertEquals(0L, input.state.value.pendingPressCount)
            port.observer.result(PressRaceInputResult("id-1", "APPLIED", "2026-10-27", mapOf("ANGRY" to 100)))
            assertEquals(
                100L,
                input.state.value.myToday
                    ?.total,
            )
        }

    @Test fun delayedWriterDoesNotExtendDeadlineAndQueuedNeverSentInputsCanBeCancelled() =
        runTest {
            val port = InputPort()
            val input = input(port)
            port.gate = CompletableDeferred()
            repeat(100) { input.accept(EmotionState.ANGRY) }
            runCurrent()
            advanceTimeBy(30_000)
            assertFalse(input.state.value.isOutcomeUnknown)
            port.state.value = PressRaceSessionState()
            runCurrent()
            port.gate!!.complete(Unit)
            runCurrent()
            assertTrue(port.sent.isEmpty())
            assertEquals(0L, input.state.value.pendingPressCount)
        }

    @Test fun rejectedInputRemovesOnlyItsOwnOptimismAndUnknownResultsKeepIt() =
        runTest {
            val port = InputPort()
            val input = input(port)
            repeat(100) { input.accept(EmotionState.ANGRY) }
            runCurrent()
            input.accept(EmotionState.EXHAUSTED)
            port.observer.result(PressRaceInputResult("id-1", "PENDING"))
            assertEquals(101L, input.state.value.pendingPressCount)
            port.observer.result(PressRaceInputResult("id-1", "REJECTED", code = "RATE_LIMITED"))
            assertEquals(1L, input.state.value.pendingPressCount)
            assertEquals(
                0L,
                input.state.value.myToday
                    ?.total,
            )
        }
}

private class InputPort : PressRaceInputPort {
    override val state = MutableStateFlow(PressRaceSessionState())
    val ready = PressRaceReady("server", "connection", "2026-10-27", emptyMap(), emptyList())
    lateinit var observer: PressRaceInputObserver
    val sent = mutableListOf<Pair<Long, String>>()
    var gate: CompletableDeferred<Unit>? = null
    var clock: () -> Long = { 0L }

    val recoveryRequests = mutableListOf<String>()

    override fun requestRecovery(generation: String) {
        recoveryRequests += generation
    }

    override fun observeInputs(observer: PressRaceInputObserver) {
        this.observer = observer
    }

    override suspend fun sendInput(
        text: String,
        generation: String,
        beforeAttempt: suspend () -> Boolean,
    ): Boolean {
        gate?.await()
        if (!beforeAttempt()) return false
        sent += clock() to text
        return true
    }
}
