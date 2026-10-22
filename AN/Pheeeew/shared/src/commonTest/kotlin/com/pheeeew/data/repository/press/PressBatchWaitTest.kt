package com.pheeeew.data.repository.press

import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.data.repository.PressRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressSender
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PressBatchWaitTest {
    @Test
    fun `inputs during post use remaining deadline after request interval`() =
        runTest {
            for (sendMillis in listOf(100L, 500L, 1_000L, 3_000L)) {
                val origin = testScheduler.currentTime
                val f = Fixture(this, 0, sendMillis = sendMillis)
                f.start()
                f.tap()
                advanceTimeBy(550)
                runCurrent()
                f.tap()
                val expected = origin + maxOf(1_050L, 600L + sendMillis)
                advanceTimeBy(expected - testScheduler.currentTime - 1)
                runCurrent()
                assertEquals(listOf(origin + 500), f.starts)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(listOf(origin + 500, expected), f.starts)
                advanceTimeBy(sendMillis + 1)
                runCurrent()
                assertEquals(
                    2L,
                    f.repository.state.value.myToday
                        ?.total,
                )
                assertEquals(1, f.maxConcurrent)
            }
        }

    @Test
    fun `input during all statistics read waits until original deadline`() =
        runTest {
            val f = Fixture(this, 200)
            f.start()
            f.tap()
            advanceTimeBy(1_650)
            runCurrent()
            assertEquals(1, f.allReads)
            assertEquals(0, f.completedAllReads)
            f.tap()
            advanceTimeBy(499)
            runCurrent()
            assertEquals(listOf(500L), f.starts)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(500L, 2_150L), f.starts)
        }

    @Test
    fun `slow aggregate read does not postpone the input deadline`() =
        runTest {
            val f = Fixture(this, 1_000)
            f.start()
            f.tap()
            advanceTimeBy(1_650)
            runCurrent()
            assertEquals(1, f.allReads)
            assertEquals(0, f.completedAllReads)
            f.tap()
            advanceTimeBy(499)
            runCurrent()
            assertEquals(listOf(500L), f.starts)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(500L, 2_150L), f.starts)
            assertEquals(0, f.completedAllReads)
        }

    @Test
    fun `additional inputs do not reset first input deadline`() =
        runTest {
            val f = Fixture(this, 200)
            f.start()
            f.tap()
            advanceTimeBy(650)
            runCurrent()
            f.tap()
            advanceTimeBy(100)
            runCurrent()
            f.tap(EmotionState.IRRITATED)
            advanceTimeBy(400)
            runCurrent()
            assertEquals(listOf(500L, 1_150L), f.starts)
            assertEquals(2, f.batches.last().totalCount)
        }

    @Test
    fun `both batch thresholds during read preserve early send`() =
        runTest {
            for (mixed in listOf(false, true)) {
                val f = Fixture(this, 200)
                f.start()
                f.tap()
                advanceTimeBy(1_650)
                runCurrent()
                assertEquals(1, f.allReads)
                assertEquals(0, f.completedAllReads)
                val count = if (mixed) 100 else 30
                repeat(count) { f.tap(EmotionState.entries[if (mixed) it % 5 else 0]) }
                advanceTimeBy(150)
                runCurrent()
                assertEquals(2, f.starts.size)
                assertEquals(count, f.batches.last().totalCount)
            }
        }

    @Test
    fun `threshold wakes the remaining wait after statistics completes`() =
        runTest {
            val f = Fixture(this, 200)
            f.start()
            f.tap()
            advanceTimeBy(1_650)
            runCurrent()
            assertEquals(1, f.allReads)
            assertEquals(0, f.completedAllReads)
            f.tap()
            advanceTimeBy(250)
            runCurrent()
            assertEquals(listOf(500L), f.starts)
            repeat(29) { f.tap() }
            runCurrent()
            assertEquals(listOf(500L, 1_900L), f.starts)
            assertEquals(30, f.batches.last().totalCount)
        }

    @Test
    fun `deferred statistics refresh also preserves the input deadline`() =
        runTest {
            val f = Fixture(this, 200, sendMillis = 1_000)
            f.start()
            f.tap()
            advanceTimeBy(650)
            runCurrent()
            f.repository.refreshToday(force = true)
            advanceTimeBy(950)
            runCurrent()
            assertEquals(1, f.allReads)
            assertEquals(0, f.completedAllReads)
            f.tap()
            advanceTimeBy(499)
            runCurrent()
            assertEquals(listOf(500L), f.starts)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(500L, 2_100L), f.starts)
        }

    @Test
    fun `aggregate refresh skips ticks while post is in flight`() =
        runTest {
            val f = Fixture(this, 200)
            f.start()
            f.tap()
            advanceTimeBy(700)
            runCurrent()
            f.sendMillis = 3_000
            f.tap()
            advanceTimeBy(2_900)
            runCurrent()
            assertEquals(0, f.allReads)
            advanceTimeBy(1_100)
            runCurrent()
            assertEquals(1, f.allReads)
        }

    @Test
    fun `reconnect resumes offline inputs without another batch wait`() =
        runTest {
            val connectivity = MutableStateFlow(false)
            val f = Fixture(this, 200, connectivity)
            f.start()
            f.tap()
            advanceTimeBy(100)
            runCurrent()
            connectivity.value = true
            runCurrent()
            assertEquals(listOf(100L), f.starts)
        }

    @Test
    fun `retry resumes fixed write at retry deadline without extra batch wait`() =
        runTest {
            val f = Fixture(this, 200)
            f.start()
            f.nextFailure = PressSendResult.RetryAfter(1_000L)
            f.tap()
            advanceTimeBy(1_599)
            runCurrent()
            assertEquals(listOf(500L), f.starts)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(500L, 1_600L), f.starts)
            assertEquals(f.batches.first(), f.batches.last())
        }

    @Test
    fun `compare continuous inputs with delayed statistics reads`() =
        runTest {
            for (mixed in listOf(false, true)) {
                val f = Fixture(this, 50, sendMillis = 40)
                f.start()
                repeat(100) {
                    f.tap(EmotionState.entries[if (mixed) it % 5 else 0])
                    runCurrent()
                    advanceTimeBy(100)
                    runCurrent()
                }
                val inputEnd = testScheduler.currentTime
                while (f.repository.state.value.pendingPressCount > 0 &&
                    testScheduler.currentTime < inputEnd + 60_000
                ) {
                    advanceTimeBy(1)
                    runCurrent()
                }
                assertEquals(0L, f.repository.state.value.pendingPressCount)
                assertEquals(
                    100L,
                    f.repository.state.value.myToday
                        ?.total,
                )
                assertEquals(100, f.confirmations.size)
                assertEquals(1, f.maxConcurrent)
                val sorted = f.confirmations.sorted()

                fun percentile(p: Double) = sorted[ceil(sorted.size * p).toInt() - 1]
                println(
                    "BATCH_WAIT_PROBE,mixed=$mixed,sends=${f.starts.size},sizes=${f.batches.joinToString(
                        "/",
                    ) { it.totalCount.toString() }},p50=${percentile(
                        .5,
                    )},p95=${percentile(.95)},peak=${f.peakPending},drain=${testScheduler.currentTime - inputEnd}",
                )
            }
        }

    @Test
    fun `compare post and statistics latency combinations`() =
        runTest {
            for (post in listOf(40L, 500L, 1_000L)) {
                for (get in listOf(50L, 500L, 3_000L)) {
                    val f = Fixture(this, get, sendMillis = post)
                    f.start()
                    repeat(100) {
                        f.tap()
                        advanceTimeBy(100)
                        runCurrent()
                    }
                    val inputEnd = testScheduler.currentTime
                    while (f.repository.state.value.pendingPressCount > 0 &&
                        testScheduler.currentTime < inputEnd + 60_000
                    ) {
                        advanceTimeBy(10)
                        runCurrent()
                    }
                    // Include the final aggregate read in the request accounting.
                    advanceTimeBy(get + 1)
                    runCurrent()
                    assertEquals(0L, f.repository.state.value.pendingPressCount)
                    assertEquals(100, f.confirmations.size)
                    assertEquals(1, f.maxConcurrent)
                    val sorted = f.confirmations.sorted()
                    println(
                        "LATENCY_PROBE,post=$post,get=$get,posts=${f.starts.size}," +
                            "gets=${f.allReads},p50=${sorted[49]},p95=${sorted[94]},peak=${f.peakPending}",
                    )
                }
            }
        }

    @Test
    fun `compare spaced bursts with aggregate read latency`() =
        runTest {
            for (get in listOf(50L, 500L, 3_000L)) {
                val f = Fixture(this, get, sendMillis = 40)
                f.start()
                repeat(10) {
                    repeat(5) {
                        f.tap()
                        advanceTimeBy(50)
                        runCurrent()
                    }
                    advanceTimeBy(750)
                    runCurrent()
                }
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(50, f.confirmations.size)
                assertEquals(0L, f.repository.state.value.pendingPressCount)
                assertEquals(1, f.maxConcurrent)
                val sorted = f.confirmations.sorted()
                println(
                    "BURST_PROBE,get=$get,posts=${f.starts.size},gets=${f.allReads}," +
                        "p50=${sorted[24]},p95=${sorted[47]},peak=${f.peakPending}",
                )
            }
        }

    @Test
    fun `aggregate requests coalesce without delaying confirmed inputs`() =
        runTest {
            val f = Fixture(this, 50, sendMillis = 40)
            f.start()
            repeat(100) {
                f.tap()
                advanceTimeBy(100)
                runCurrent()
            }
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(100, f.confirmations.size)
            assertEquals(0L, f.repository.state.value.pendingPressCount)
            kotlin.test.assertTrue(f.allReads <= 7, "aggregate reads=${f.allReads}")
            assertEquals(
                100L,
                f.repository.state.value.allToday
                    ?.total,
            )
            assertEquals(0L, f.repository.state.value.optimisticAllPressCount)
        }

    @Test
    fun `aggregate started before a new write never rolls back its coverage`() =
        runTest {
            val f = Fixture(this, 2_000, sendMillis = 100, captureAllAtStart = true)
            f.start()
            f.tap()
            advanceTimeBy(1_700)
            runCurrent()
            f.tap()
            advanceTimeBy(1_900)
            runCurrent()
            assertEquals(2, f.confirmations.size)
            assertEquals(0L, f.repository.state.value.pendingPressCount)
            // The stale response must leave the acknowledged optimistic coverage intact.
            assertEquals(2L, f.repository.state.value.optimisticAllPressCount)
            advanceTimeBy(3_100)
            runCurrent()
            assertEquals(
                2L,
                f.repository.state.value.allToday
                    ?.total,
            )
            assertEquals(0L, f.repository.state.value.optimisticAllPressCount)
        }

    @Test
    fun `aggregate returned during an in flight write preserves optimistic totals`() =
        runTest {
            val f = Fixture(this, 2_000, sendMillis = 100, captureAllAtStart = true)
            f.start()
            f.tap()
            advanceTimeBy(1_700)
            runCurrent()
            f.sendMillis = 3_000
            f.tap()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(1L, f.repository.state.value.pendingPressCount)
            assertEquals(
                0L,
                f.repository.state.value.allToday
                    ?.total,
            )
            assertEquals(2L, f.repository.state.value.optimisticAllPressCount)
            advanceTimeBy(7_000)
            runCurrent()
            assertEquals(
                2L,
                f.repository.state.value.allToday
                    ?.total,
            )
            assertEquals(0L, f.repository.state.value.optimisticAllPressCount)
            assertEquals(2, f.confirmations.size)
        }

    @Test
    fun `zero aggregate interval is rejected`() =
        runTest {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                Fixture(this, 200, aggregateRefreshMillis = 0)
            }
        }

    private class Fixture(
        private val scope: TestScope,
        private val allReadMillis: Long,
        connectivity: MutableStateFlow<Boolean>? = null,
        var sendMillis: Long = 100,
        private val captureAllAtStart: Boolean = false,
        aggregateRefreshMillis: Long = 1_000L,
    ) : PressStatisticsDataSource,
        PressSender {
        private val counts = EmotionState.entries.associateWith { 0L }.toMutableMap()
        private var initialRead = true
        val starts = mutableListOf<Long>()
        val batches = mutableListOf<PressBatch>()
        val confirmations = mutableListOf<Long>()
        private val inputs = EmotionState.entries.associateWith { ArrayDeque<Long>() }
        var completedAllReads = 0
        var allReads = 0
        var peakPending = 0L
        private var concurrent = 0
        var maxConcurrent = 0
        var nextFailure: PressSendResult? = null
        val repository =
            PressRepositoryImpl(
                this,
                this,
                scope.backgroundScope,
                connectivity = connectivity,
                aggregateRefreshMillis = PressDelayMillis(aggregateRefreshMillis),
                retryPolicy = PressRetryPolicy(jitter = { 1.0 }),
                nowMillis = { PressMonotonicMillis(scope.testScheduler.currentTime) },
            )

        fun start() {
            repository.refreshToday()
            scope.runCurrent()
            initialRead = false
        }

        fun tap(emotion: EmotionState = EmotionState.ANGRY) {
            inputs.getValue(emotion).addLast(scope.testScheduler.currentTime)
            repository.accept(emotion)
            peakPending = maxOf(peakPending, repository.state.value.pendingPressCount)
        }

        override suspend fun findMyToday() =
            PressReadResult.Loaded(MyDailyPressSnapshot("2026-10-16", counts.toMap(), counts.values.sum()))

        override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> {
            val captured = counts.values.sum()
            if (!initialRead) {
                allReads++
                delay(allReadMillis)
                completedAllReads++
            }
            return PressReadResult.Loaded(
                AllDailyPressSnapshot("2026-10-16", if (captureAllAtStart) captured else counts.values.sum()),
            )
        }

        override suspend fun send(batch: PressBatch): PressSendResult {
            starts += scope.testScheduler.currentTime
            batches += batch
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            delay(sendMillis)
            concurrent--
            nextFailure?.let {
                nextFailure = null
                return it
            }
            batch.counts.forEach { (emotion, count) ->
                repeat(count) {
                    confirmations +=
                        scope.testScheduler.currentTime - inputs.getValue(emotion).removeFirst()
                }
                counts[emotion] = counts.getValue(emotion) + count
            }
            return PressSendResult.Accepted(MyDailyPressTotals(counts.toMap(), counts.values.sum()))
        }
    }
}
