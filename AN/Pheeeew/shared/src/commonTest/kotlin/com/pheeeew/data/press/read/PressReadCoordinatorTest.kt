package com.pheeeew.data.press.read

import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressReadCoordinatorTest {
    @Test
    fun `refreshes during a pending read coalesce into one subsequent pair`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        reads++
                        if (reads == 1) release.await()
                        PressReadResult.Loaded(personalSnapshot())
                    }),
                )
            fixture.coordinator.refreshToday(force = true)
            runCurrent()
            repeat(5) { fixture.coordinator.refreshToday(force = true) }
            assertEquals(1, reads)
            release.complete(Unit)
            runCurrent()
            assertEquals(2, reads)
            assertEquals(1, fixture.events.count { it == PressReadEvent.PairIdle })
        }

    @Test
    fun `refresh requested during a write waits for its completion`() =
        runTest {
            val fixture = Fixture(this)
            fixture.coordinator.refreshToday(force = true, writeWorkerActive = true)
            runCurrent()
            assertTrue(fixture.events.isEmpty())
            assertTrue(fixture.coordinator.resumeRequestedRead())
            runCurrent()
            assertEquals(1, fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().size)
            assertFalse(fixture.coordinator.resumeRequestedRead())
        }

    @Test
    fun `fresh paired results skip reads until expiry while force bypasses freshness`() =
        runTest {
            val fixture = Fixture(this)
            fixture.coordinator.refreshToday()
            runCurrent()
            fixture.coordinator.refreshToday()
            advanceTimeBy(4_999)
            fixture.coordinator.refreshToday()
            runCurrent()
            assertEquals(1, fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().size)
            fixture.coordinator.refreshToday(force = true)
            runCurrent()
            advanceTimeBy(5_000)
            fixture.coordinator.refreshToday()
            runCurrent()
            assertEquals(3, fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().size)
        }

    @Test
    fun `date mismatch retries once and keeps both mismatching snapshots unavailable`() =
        runTest {
            var reads = 0
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        reads++
                        PressReadResult.Loaded(personalSnapshot())
                    }, aggregate = { PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-23", 10)) }),
                )
            fixture.coordinator.refreshToday()
            runCurrent()
            assertEquals(2, reads)
            val result = fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().single()
            assertEquals(null, result.personal)
            assertEquals(null, result.aggregate)
        }

    @Test
    fun `paired result retains write coverage captured before the request`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        release.await()
                        PressReadResult.Loaded(personalSnapshot())
                    }),
                )
            fixture.coordinator.updateWriteContext(PressReadContext(acceptedPressCoverage = 5))
            fixture.coordinator.refreshToday()
            runCurrent()
            fixture.coordinator.updateWriteContext(PressReadContext(acceptedPressCoverage = 9))
            release.complete(Unit)
            runCurrent()
            assertEquals(
                5,
                fixture.events
                    .filterIsInstance<PressReadEvent.PairCompleted>()
                    .single()
                    .context.acceptedPressCoverage,
            )
        }

    @Test
    fun `paired response superseded by a successful write is not published`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        release.await()
                        PressReadResult.Loaded(personalSnapshot())
                    }),
                )
            fixture.coordinator.refreshToday()
            runCurrent()
            fixture.coordinator.personalWriteSucceeded()
            release.complete(Unit)
            runCurrent()
            assertTrue(fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().isEmpty())
            assertTrue(PressReadEvent.PairIdle in fixture.events)
        }

    @Test
    fun `aggregate response superseded by a successful write is discarded then refreshed`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var aggregateReads = 0
            val fixture =
                Fixture(
                    this,
                    source(aggregate = {
                        aggregateReads++
                        if (aggregateReads == 1) release.await()
                        PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-22", aggregateReads.toLong()))
                    }),
                )
            fixture.coordinator.personalWriteSucceeded()
            fixture.coordinator.scheduleAggregateRefresh()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            fixture.coordinator.updateWriteContext(PressReadContext(acceptedPressCoverage = 2))
            fixture.coordinator.personalWriteSucceeded()
            release.complete(Unit)
            runCurrent()
            assertTrue(PressReadEvent.AggregateDiscarded in fixture.events)
            assertTrue(fixture.events.filterIsInstance<PressReadEvent.AggregateCompleted>().isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
            val result = fixture.events.filterIsInstance<PressReadEvent.AggregateCompleted>().single()
            assertEquals(2, result.snapshot?.total)
            assertEquals(2, result.context.acceptedPressCoverage)
        }

    @Test
    fun `aggregate refresh skips a running write without losing the pending refresh`() =
        runTest {
            val fixture = Fixture(this)
            fixture.coordinator.updateWriteContext(PressReadContext(writeInFlight = true))
            fixture.coordinator.personalWriteSucceeded()
            fixture.coordinator.scheduleAggregateRefresh()
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(fixture.events.isEmpty())
            fixture.coordinator.updateWriteContext(PressReadContext())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, fixture.events.filterIsInstance<PressReadEvent.AggregateCompleted>().size)
        }

    @Test
    fun `failed statistics retry stops offline and foreground refresh recovers`() =
        runTest {
            var reads = 0
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        reads++
                        if (reads == 1) PressReadResult.Unavailable else PressReadResult.Loaded(personalSnapshot())
                    }),
                )
            fixture.coordinator.refreshToday()
            runCurrent()
            fixture.coordinator.onConnectivityChanged(false)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, reads)
            fixture.coordinator.onConnectivityChanged(true)
            fixture.coordinator.refreshToday()
            runCurrent()
            assertEquals(2, reads)
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(2, reads)
        }

    @Test
    fun `cancellation never publishes a completed snapshot`() =
        runTest {
            var cancelled = false
            val fixture =
                Fixture(
                    this,
                    source(personal = {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }),
                )
            fixture.coordinator.refreshToday()
            runCurrent()
            backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            runCurrent()
            assertTrue(cancelled)
            assertTrue(fixture.events.filterIsInstance<PressReadEvent.PairCompleted>().isEmpty())
        }

    private class Fixture(
        scope: TestScope,
        source: PressStatisticsDataSource = source(),
    ) {
        val events = mutableListOf<PressReadEvent>()
        val coordinator =
            PressReadCoordinator(
                statistics = source,
                scope = scope.backgroundScope,
                retryPolicy = PressRetryPolicy(jitter = { 1.0 }),
                aggregateRefreshMillis = PressDelayMillis(1_000),
                nowMillis = { PressMonotonicMillis(scope.testScheduler.currentTime) },
                onEvent = ::handle,
            )

        private fun handle(event: PressReadEvent) {
            events += event
            when (event) {
                is PressReadEvent.PairCompleted -> {
                    coordinator.completePairedRead(
                        event.personal != null,
                        event.aggregate != null,
                    )
                }

                is PressReadEvent.AggregateCompleted -> {
                    coordinator.completeAggregateRead(event.snapshot != null)
                }

                else -> {}
            }
        }
    }

    private companion object {
        fun source(
            personal: suspend () -> PressReadResult<MyDailyPressSnapshot> = {
                PressReadResult.Loaded(
                    personalSnapshot(),
                )
            },
            aggregate: suspend () -> PressReadResult<AllDailyPressSnapshot> = {
                PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-22", 10))
            },
        ) = object : PressStatisticsDataSource {
            override suspend fun findMyToday() = personal()

            override suspend fun findAllToday() = aggregate()
        }

        fun personalSnapshot() = MyDailyPressSnapshot("2026-10-22", EmotionState.entries.associateWith { 0L }, 0)
    }
}
