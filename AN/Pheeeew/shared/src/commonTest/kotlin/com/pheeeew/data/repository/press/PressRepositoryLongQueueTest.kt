package com.pheeeew.data.repository.press

import com.pheeeew.core.monitoring.ActivityType
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressSender
import com.pheeeew.domain.repository.press.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRepositoryLongQueueTest {
    @Test
    fun `only acknowledged batches report original input time`() =
        runTest {
            val server = Server()
            val release = CompletableDeferred<Unit>()
            val sender = Sender(server, beforeSend = { release.await() })
            val recorded = mutableListOf<Pair<ActivityType, Long>>()
            val monitoring =
                object : Monitoring by NoOpMonitoring {
                    override fun recordSuccessfulActivity(
                        type: ActivityType,
                        occurredAt: Long,
                    ) {
                        recorded += type to occurredAt
                    }
                }
            var time = 1000L
            val repository =
                PressRepositoryImpl(
                    server,
                    sender,
                    backgroundScope,
                    flushDelayMillis = 0,
                    monitoring = monitoring,
                    wallClock = { time },
                )
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertTrue(recorded.isEmpty())
            time = 90_000_000L
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(ActivityType.PERSONAL_PRESS to 1000L), recorded)
        }

    @Test
    fun `aggregate reconciliation of unknown writes does not claim activity success`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, applyBeforeUnknown = true)
            var reports = 0
            val monitoring =
                object : Monitoring by NoOpMonitoring {
                    override fun recordSuccessfulActivity(
                        type: ActivityType,
                        occurredAt: Long,
                    ) {
                        reports++
                    }
                }
            val repository =
                PressRepositoryImpl(server, sender, backgroundScope, flushDelayMillis = 0, monitoring = monitoring)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertEquals(0, reports)
        }

    @Test
    fun `ten thousand offline taps drain exactly once after reconnect without concurrent writes`() =
        runTest {
            val connection = MutableStateFlow(false)
            val server = Server()
            val sender = Sender(server, time = { testScheduler.currentTime })
            val repository = repository(server, sender, connection)
            repository.refreshToday()
            runCurrent()
            repeat(10_000) { assertEquals(PressAcceptance.Accepted, repository.accept(EmotionState.entries[it % 5])) }
            runCurrent()
            assertEquals(10_000L, repository.state.value.pendingPressCount)
            assertEquals(
                10_000L,
                repository.state.value.optimisticCounts.values
                    .sum(),
            )
            assertEquals(0, sender.batches.size)

            connection.value = true
            runCurrent()
            repeat(5) { repository.onForeground() }
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(0L, repository.state.value.pendingPressCount)
            assertEquals(
                10_000L,
                repository.state.value.myToday
                    ?.total,
            )
            assertTrue(server.counts.values.all { it == 2_000L })
            assertTrue(sender.batches.all { it.totalCount <= 100 && it.counts.values.all { count -> count <= 30 } })
            assertEquals(
                sender.batches.size,
                sender.batches
                    .map { it.requestId }
                    .distinct()
                    .size,
            )
            assertEquals(1, sender.maxConcurrent)
            assertTrue(sender.times.zipWithNext().all { (before, after) -> after - before >= 10L })
        }

    @Test
    fun `full batch wakes the initial flush timer without waiting five hundred millis`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.ContractPending)
            val repository =
                PressRepositoryImpl(server, sender, backgroundScope, nowMillis = { testScheduler.currentTime })
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertEquals(0, sender.batches.size)
            repeat(29) { repository.accept(EmotionState.ANGRY) }
            runCurrent()
            assertEquals(1, sender.batches.size)
            assertEquals(30, sender.batches.single().totalCount)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun `contract pending retains ten thousand inputs without repeated sender calls`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.ContractPending)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.FRUSTRATED)
            runCurrent()
            repeat(9_999) { assertEquals(PressAcceptance.Accepted, repository.accept(EmotionState.ANGRY)) }
            repository.onForeground()
            repository.retryUnsent()
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(10_000L, repository.state.value.pendingPressCount)
            assertEquals(1, sender.batches.size)
            assertEquals(PressSessionNotice.WaitingForApiContract, repository.state.value.notice)
        }

    @Test
    fun `retry after keeps the batch identity and new taps separate even with repeated wakeups`() =
        runTest {
            val connection = MutableStateFlow(true)
            val server = Server()
            val sender = Sender(server, PressSendResult.RetryAfter(5_000L))
            val repository = repository(server, sender, connection)
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.accept(EmotionState.FRUSTRATED)
            repository.accept(EmotionState.FRUSTRATED)
            repeat(10) {
                repository.onForeground()
                repository.retryUnsent()
            }
            runCurrent()
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(1, sender.batches.size)
            advanceTimeBy(1)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(3, sender.batches.size)
            assertEquals(sender.batches[0], sender.batches[1])
            assertNotEquals(sender.batches[1].requestId, sender.batches[2].requestId)
            assertEquals(mapOf(EmotionState.ANGRY to 1), sender.batches[1].counts)
            assertEquals(mapOf(EmotionState.FRUSTRATED to 2), sender.batches[2].counts)
            assertEquals(3L, server.counts.values.sum())
            assertEquals(0L, repository.state.value.pendingPressCount)
        }

    @Test
    fun `lost response retries identical id inside server window and applies the batch once`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, window = 10_000L, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertTrue(repository.state.value.isOutcomeUnknown)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(2, sender.batches.size)
            assertEquals(sender.batches[0], sender.batches[1])
            assertEquals(1L, server.counts.values.sum())
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertFalse(repository.state.value.isOutcomeUnknown)
        }

    @Test
    fun `unconfirmed batch retires without replay and drains later input`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repeat(10_000) { assertEquals(PressAcceptance.Accepted, repository.accept(EmotionState.EXHAUSTED)) }
            repository.retryUnsent()
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(1, sender.batches.count { it.requestId == sender.batches.first().requestId })
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertEquals(
                10_000L,
                repository.state.value.myToday
                    ?.total,
            )
            assertEquals(
                10_000L,
                repository.state.value.allToday
                    ?.total,
            )
            assertFalse(repository.state.value.isOutcomeUnknown)
        }

    @Test
    fun `unconfirmed batch retires after three successful reads without guessing or replay`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.refreshToday(force = true)
            repository.retryUnsent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(0L, server.counts.values.sum())
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertFalse(repository.state.value.isOutcomeUnknown)
            assertEquals(0L, repository.state.value.optimisticAllPressCount)
            assertEquals(1, sender.batches.size)
        }

    @Test
    fun `later snapshot confirms an unknown batch and sends queued input`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.accept(EmotionState.EXHAUSTED)
            server.apply(sender.batches.first())
            repository.refreshToday(force = true)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(2, sender.batches.size)
            assertNotEquals(sender.batches[0].requestId, sender.batches[1].requestId)
            assertEquals(
                2L,
                repository.state.value.myToday
                    ?.total,
            )
            assertEquals(
                2L,
                repository.state.value.allToday
                    ?.total,
            )
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertFalse(repository.state.value.isOutcomeUnknown)
            assertFalse(repository.state.value.hasAllError)
        }

    @Test
    fun `failed reads do not exhaust unknown reconciliation and background reads recover`() =
        runTest {
            val server = Server()
            val sender =
                Sender(
                    server,
                    PressSendResult.OutcomeUnknown,
                    applyBeforeUnknown = false,
                    afterSend = { server.failReads = true },
                )
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1L, repository.state.value.pendingPressCount)
            assertTrue(repository.state.value.isOutcomeUnknown)
            server.apply(sender.batches.first())
            server.failReads = false
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, sender.batches.size)
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertEquals(
                1L,
                repository.state.value.myToday
                    ?.total,
            )
            assertEquals(
                1L,
                repository.state.value.allToday
                    ?.total,
            )
            assertFalse(repository.state.value.isOutcomeUnknown)
        }

    @Test
    fun `new server date retires only the unknown batch and resumes queued input`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.accept(EmotionState.EXHAUSTED)
            server.pressDate = "2026-10-09"
            repository.refreshToday(force = true)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(2, sender.batches.size)
            assertEquals(
                "2026-10-09",
                repository.state.value.myToday
                    ?.pressDate,
            )
            assertEquals(
                1L,
                repository.state.value.myToday
                    ?.total,
            )
            assertEquals(
                1L,
                repository.state.value.allToday
                    ?.total,
            )
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertFalse(repository.state.value.isOutcomeUnknown)
        }

    @Test
    fun `unknown batch is not replayed after server id retention expires`() =
        runTest {
            val server = Server()
            val sender = Sender(server, PressSendResult.OutcomeUnknown, window = 500L, applyBeforeUnknown = false)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            advanceTimeBy(10_000)
            repository.retryUnsent()
            runCurrent()
            assertEquals(1, sender.batches.size)
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertFalse(repository.state.value.isOutcomeUnknown)
        }

    @Test
    fun `failed aggregate read retries the read without replaying an accepted write`() =
        runTest {
            val server = Server()
            val sender = Sender(server, afterSend = { server.failReads = true })
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.accept(EmotionState.FRUSTRATED)
            repository.accept(EmotionState.FRUSTRATED)
            assertEquals(2L, repository.state.value.pendingPressCount)
            assertEquals(
                1L,
                repository.state.value.myToday
                    ?.total,
            )
            server.failReads = false
            sender.afterSend = {}
            advanceTimeBy(1_000)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(2, sender.batches.size)
            assertNotEquals(sender.batches[0].requestId, sender.batches[1].requestId)
            assertEquals(3L, server.counts.values.sum())
            assertEquals(0L, repository.state.value.pendingPressCount)
        }

    @Test
    fun `new input survives an in flight batch rejection`() =
        runTest {
            val server = Server()
            val release = CompletableDeferred<Unit>()
            val sender = Sender(server, PressSendResult.Rejected, beforeSend = { release.await() })
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertTrue(repository.state.value.isSending)
            repeat(1_000) { repository.accept(EmotionState.EXHAUSTED) }
            release.complete(Unit)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(0L, server.counts.getValue(EmotionState.ANGRY))
            assertEquals(1_000L, server.counts.getValue(EmotionState.EXHAUSTED))
            assertEquals(0L, repository.state.value.pendingPressCount)
        }

    @Test
    fun `accepted batch response replaces the cached baseline without double adding optimism`() =
        runTest {
            val server = Server()
            val sender = Sender(server)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            assertEquals(0L, repository.state.value.pendingPressCount)
            assertEquals(
                2L,
                repository.state.value.myToday
                    ?.total,
            )
            assertTrue(
                repository.state.value.optimisticCounts
                    .isEmpty(),
            )
            assertEquals(1, sender.batches.size)
        }

    @Test
    fun `cancellation after server apply is unknown rather than requeued under a new id`() =
        runTest {
            val server = Server()
            val sender = Sender(server, afterSend = { throw CancellationException("response lost") })
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            repository.accept(EmotionState.ANGRY)
            runCurrent()
            repository.accept(EmotionState.FRUSTRATED)
            repository.retryUnsent()
            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(repository.state.value.isOutcomeUnknown)
            assertEquals(2L, repository.state.value.pendingPressCount)
            assertEquals(1, sender.batches.size)
            assertEquals(1L, server.counts.values.sum())
        }

    @Test
    fun `numeric range overflow rejects only the overflowing tap`() =
        runTest {
            val server = Server()
            server.counts[EmotionState.ANGRY] = Long.MAX_VALUE - 1L
            val sender = Sender(server, PressSendResult.ContractPending)
            val repository = repository(server, sender)
            repository.refreshToday()
            runCurrent()
            assertEquals(PressAcceptance.Accepted, repository.accept(EmotionState.ANGRY))
            assertEquals(PressAcceptance.NumericLimit, repository.accept(EmotionState.ANGRY))
            assertEquals(1L, repository.state.value.pendingPressCount)
            assertEquals(1L, repository.state.value.optimisticCounts[EmotionState.ANGRY])
            assertEquals(PressSessionNotice.NumericLimit, repository.state.value.notice)
        }

    private fun TestScope.repository(
        server: Server,
        sender: Sender,
        connectivity: MutableStateFlow<Boolean>? = null,
    ) = PressRepositoryImpl(
        server,
        sender,
        backgroundScope,
        flushDelayMillis = 0L,
        connectivity = connectivity,
        retryPolicy = PressRetryPolicy(minRequestIntervalMillis = 10L, jitter = { 1.0 }),
        nowMillis = { testScheduler.currentTime },
    )

    private class Server : PressStatisticsDataSource {
        val counts = EmotionState.entries.associateWith { 0L }.toMutableMap()
        var failReads = false
        var pressDate = "2026-10-08"

        override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> {
            if (failReads) return PressReadResult.Unavailable
            return PressReadResult.Loaded(MyDailyPressSnapshot(pressDate, counts.toMap(), counts.values.sum()))
        }

        override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> =
            if (failReads) {
                PressReadResult.Unavailable
            } else {
                PressReadResult.Loaded(
                    AllDailyPressSnapshot(pressDate, counts.values.sum()),
                )
            }

        fun apply(batch: PressBatch) {
            batch.counts.forEach { (emotion, count) -> counts[emotion] = counts.getValue(emotion) + count }
        }

        fun todayTotals() = MyDailyPressTotals(counts.toMap(), counts.values.sum())
    }

    private class Sender(
        private val server: Server,
        vararg results: PressSendResult,
        private val window: Long? = null,
        private val applyBeforeUnknown: Boolean = true,
        private val time: () -> Long = { 0L },
        private val beforeSend: suspend () -> Unit = {},
        var afterSend: () -> Unit = {},
    ) : PressSender {
        override val idempotencyWindowMillis: Long? = window
        private val scripted = ArrayDeque(results.toList())
        private val applied = mutableSetOf<String>()
        val batches = mutableListOf<PressBatch>()
        val times = mutableListOf<Long>()
        private var concurrent = 0
        var maxConcurrent = 0

        override suspend fun send(batch: PressBatch): PressSendResult {
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                batches += batch
                times += time()
                beforeSend()
                val result = if (scripted.isEmpty()) acceptedMarker() else scripted.removeFirst()
                if ((
                        result is PressSendResult.Accepted ||
                            (result == PressSendResult.OutcomeUnknown && applyBeforeUnknown)
                    ) &&
                    applied.add(batch.requestId)
                ) {
                    server.apply(batch)
                }
                afterSend()
                return if (result is PressSendResult.Accepted) {
                    PressSendResult.Accepted(server.todayTotals())
                } else {
                    result
                }
            } finally {
                concurrent--
            }
        }
    }

    private companion object {
        fun acceptedMarker() =
            PressSendResult.Accepted(
                MyDailyPressTotals(EmotionState.entries.associateWith { 0L }, 0L),
            )
    }
}
