package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressSender
import com.pheeeew.domain.repository.press.PressStatisticsDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Virtual-time measurements of the production repository defaults, never server benchmarks. */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalPressLoadProbeTest {
    @Test
    fun `single input and six sustained input scenarios with production defaults`() {
        probe("single", 1, 100L, false)
        for (responseMillis in listOf(100L, 1_000L, 3_000L)) {
            probe("same", 100, responseMillis, false)
            probe("mixed", 100, responseMillis, true)
        }
    }

    @Test
    fun `both thresholds flush before timer with production defaults`() =
        runTest {
            for (mixed in listOf(false, true)) {
                val server = Server()
                val sizes = mutableListOf<Int>()
                val sender =
                    PressSender { batch ->
                        sizes += batch.totalCount
                        PressSendResult.ContractPending
                    }
                val repository = PressRepositoryImpl(server, sender, backgroundScope)
                repository.refreshToday()
                runCurrent()
                val threshold = if (mixed) 100 else 30
                repeat(threshold - 1) { index ->
                    repository.accept(EmotionState.entries[if (mixed) index % 5 else 0])
                    runCurrent()
                }
                assertTrue(sizes.isEmpty())
                repository.accept(EmotionState.entries[if (mixed) (threshold - 1) % 5 else 0])
                runCurrent()
                assertEquals(listOf(threshold), sizes)
                assertEquals(0L, testScheduler.currentTime)
            }
        }

    private fun probe(
        label: String,
        inputCount: Int,
        responseMillis: Long,
        mixed: Boolean,
    ) = runTest {
        val server = Server()
        val queued = EmotionState.entries.associateWith { ArrayDeque<Long>() }
        val confirmed = mutableListOf<Long>()
        val starts = mutableListOf<Long>()
        val batchSizes = mutableListOf<Int>()
        var concurrent = 0
        var maxConcurrent = 0
        val sender =
            PressSender { batch ->
                starts += testScheduler.currentTime
                batchSizes += batch.totalCount
                concurrent++
                maxConcurrent = maxOf(maxConcurrent, concurrent)
                assertTrue(batch.totalCount <= 100 && batch.counts.values.all { it <= 30 })
                val inputs =
                    batch.counts.flatMap { (emotion, count) ->
                        List(count) { queued.getValue(emotion).removeFirst() }
                    }
                delay(responseMillis)
                batch.counts.forEach { (emotion, count) ->
                    server.counts[emotion] = server.counts.getValue(emotion) + count
                }
                confirmed += inputs.map { testScheduler.currentTime - it }
                concurrent--
                PressSendResult.Accepted(MyDailyPressTotals(server.counts.toMap(), server.counts.values.sum()))
            }
        val repository =
            PressRepositoryImpl(
                server,
                sender,
                backgroundScope,
                nowMillis = { testScheduler.currentTime },
                wallClock = { 1_791_936_000_000L + testScheduler.currentTime },
                measurement = { error("An observation failure must not affect sending") },
            )
        repository.refreshToday()
        runCurrent()
        var peakPending = 0L
        repeat(inputCount) { index ->
            val emotion = EmotionState.entries[if (mixed) index % EmotionState.entries.size else 0]
            queued.getValue(emotion).addLast(testScheduler.currentTime)
            assertEquals(PressAcceptance.Accepted, repository.accept(emotion))
            peakPending = maxOf(peakPending, repository.state.value.pendingPressCount)
            runCurrent()
            advanceTimeBy(100L)
            runCurrent()
        }
        // Input window includes the final 100ms slot: 100 inputs at t=0..9900, ends at t=10000.
        val inputEnd = testScheduler.currentTime
        val deadline = inputEnd + 60_000L
        while (repository.state.value.pendingPressCount > 0L && testScheduler.currentTime < deadline) {
            advanceTimeBy(1L)
            runCurrent()
        }
        assertEquals(0L, repository.state.value.pendingPressCount)
        assertEquals(inputCount.toLong(), server.counts.values.sum())
        assertEquals(inputCount, confirmed.size)
        assertEquals(1, maxConcurrent)
        EmotionState.entries.forEachIndexed { index, emotion ->
            val expected =
                if (mixed) {
                    (0 until inputCount).count { it % EmotionState.entries.size == index }
                } else if (index ==
                    0
                ) {
                    inputCount
                } else {
                    0
                }
            assertEquals(expected.toLong(), server.counts.getValue(emotion))
        }
        if (inputCount == 1) {
            assertEquals(listOf(500L), starts)
            assertEquals(listOf(600L), confirmed)
        }
        val sorted = confirmed.sorted()

        fun percentile(fraction: Double) = sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0)]
        println(
            "PRESS_PROBE,$label,$responseMillis,$inputCount,${starts.size},${batchSizes.joinToString(
                "/",
            )},$peakPending,${percentile(
                0.50,
            )},${percentile(
                0.95,
            )},${testScheduler.currentTime - inputEnd},${server.counts.values.sum()},$maxConcurrent",
        )
    }

    private class Server : PressStatisticsDataSource {
        val counts = EmotionState.entries.associateWith { 0L }.toMutableMap()

        override suspend fun findMyToday() =
            PressReadResult.Loaded(MyDailyPressSnapshot("2026-10-14", counts.toMap(), counts.values.sum()))

        override suspend fun findAllToday() =
            PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-14", counts.values.sum()))
    }
}
