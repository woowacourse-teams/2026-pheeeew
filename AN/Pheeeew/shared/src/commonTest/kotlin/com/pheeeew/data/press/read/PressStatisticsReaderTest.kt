package com.pheeeew.data.press.read

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressStatisticsReaderTest {
    @Test
    fun `personal and aggregate reads start concurrently`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var personalStarted = false
            var aggregateStarted = false
            val reader =
                PressStatisticsReader(
                    source(
                        personal = {
                            personalStarted = true
                            release.await()
                            PressReadResult.Loaded(personalSnapshot())
                        },
                        aggregate = {
                            aggregateStarted = true
                            release.await()
                            PressReadResult.Loaded(aggregateSnapshot())
                        },
                    ),
                )
            val read = async { reader.readPair() }
            runCurrent()
            assertTrue(personalStarted)
            assertTrue(aggregateStarted)
            assertFalse(read.isCompleted)
            release.complete(Unit)
            assertEquals(
                PressStatisticsPair(
                    PressReadResult.Loaded(personalSnapshot()),
                    PressReadResult.Loaded(aggregateSnapshot()),
                ),
                read.await(),
            )
        }

    @Test
    fun `a failed personal read preserves the successful aggregate result`() =
        runTest {
            val reader =
                PressStatisticsReader(
                    source(
                        personal = { error("personal unavailable") },
                        aggregate = { PressReadResult.Loaded(aggregateSnapshot()) },
                    ),
                )
            val pair = reader.readPair()
            assertEquals(PressReadResult.Unavailable, pair.personal)
            assertEquals(PressReadResult.Loaded(aggregateSnapshot()), pair.aggregate)
            assertFalse(pair.hasDifferentDates())
        }

    @Test
    fun `cancellation propagates and cancels the sibling read`() =
        runTest {
            val aggregateStarted = CompletableDeferred<Unit>()
            var aggregateCancelled = false
            val reader =
                PressStatisticsReader(
                    source(
                        personal = {
                            aggregateStarted.await()
                            throw CancellationException("owner cancelled")
                        },
                        aggregate = {
                            try {
                                aggregateStarted.complete(Unit)
                                awaitCancellation()
                            } finally {
                                aggregateCancelled = true
                            }
                        },
                    ),
                )
            assertFailsWith<CancellationException> { reader.readPair() }
            assertTrue(aggregateCancelled)
        }

    @Test
    fun `date consistency is checked only when both snapshots are available`() {
        assertTrue(
            PressStatisticsPair(
                PressReadResult.Loaded(personalSnapshot()),
                PressReadResult.Loaded(aggregateSnapshot().copy(pressDate = "2026-10-21")),
            ).hasDifferentDates(),
        )
        assertFalse(
            PressStatisticsPair(
                PressReadResult.Loaded(personalSnapshot()),
                PressReadResult.Loaded(aggregateSnapshot()),
            ).hasDifferentDates(),
        )
        assertFalse(PressStatisticsPair(PressReadResult.Unavailable, PressReadResult.Unavailable).hasDifferentDates())
    }

    private fun source(
        personal: suspend () -> PressReadResult<MyDailyPressSnapshot>,
        aggregate: suspend () -> PressReadResult<AllDailyPressSnapshot>,
    ): PressStatisticsDataSource =
        object : PressStatisticsDataSource {
            override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> = personal()

            override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> = aggregate()
        }

    private fun personalSnapshot() = MyDailyPressSnapshot("2026-10-20", EmotionState.entries.associateWith { 0L }, 0)

    private fun aggregateSnapshot() = AllDailyPressSnapshot("2026-10-20", 10)
}
