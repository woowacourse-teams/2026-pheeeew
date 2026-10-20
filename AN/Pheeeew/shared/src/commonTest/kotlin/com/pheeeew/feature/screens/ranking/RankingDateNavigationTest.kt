package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RankingDateNavigationTest {
    @Test
    fun pressRapidNavigationKeepsLastSelection() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    PressRankingViewModel(
                        object : PressRankingSource {
                            override suspend fun load(
                                emotion: PressEmotion,
                                weeksAgo: Int,
                            ): PressRankingLoadResult {
                                calls += weeksAgo
                                delay(500)
                                return PressRankingLoadResult.Loaded(
                                    "week-$weeksAgo",
                                    "end-$weeksAgo",
                                    true,
                                    emptyList(),
                                )
                            }
                        },
                    )
                advanceUntilIdle()
                repeat(10) {
                    vm.onPreviousWeek()
                    advanceTimeBy(50)
                }
                vm.onNextWeek()
                assertEquals(9, vm.uiState.value.weeksAgo)
                advanceUntilIdle()
                assertEquals(PressRankingStatus.Ready, vm.uiState.value.status)
                assertEquals(listOf(0, 9), calls)
                assertEquals("week.9 ~ end.9", vm.uiState.value.weekLabel)
            } finally {
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun lateCancelledResponsesCannotReplaceFinalWeek() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                run {
                    val old = CompletableDeferred<Unit>()
                    val h =
                        Harness { week ->
                            if (week == 1) withContext(NonCancellable) { old.await() }
                            true
                        }
                    advanceUntilIdle()
                    h.previous()
                    advanceTimeBy(150)
                    runCurrent()
                    h.previous()
                    advanceTimeBy(150)
                    runCurrent()
                    assertEquals(2, h.week())
                    assertTrue(h.ready())
                    old.complete(Unit)
                    advanceUntilIdle()
                    assertEquals(2, h.week())
                    assertEquals("week.2 ~ end.2", h.label())
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun failedAndThrowingRequestsAllowRetryAndNextWeek() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                run {
                    var failures = true
                    val h =
                        Harness { week ->
                            if (week == 2 && failures) error("network failure")
                            !(week == 1 && failures)
                        }
                    advanceUntilIdle()
                    h.previous()
                    advanceUntilIdle()
                    assertTrue(h.failed())
                    h.previous()
                    advanceUntilIdle()
                    assertEquals(2, h.week())
                    assertTrue(h.failed())
                    failures = false
                    h.retry()
                    advanceUntilIdle()
                    assertTrue(h.ready())
                    h.next()
                    h.next()
                    advanceUntilIdle()
                    assertEquals(0, h.week())
                    assertTrue(h.ready())
                    assertFalse(h.refreshing())
                }
            } finally {
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun knownOldestBoundaryAndCurrentWeekBoundarySurviveCrossTaps() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                run {
                    val h = Harness(oldest = 1) { true }
                    advanceUntilIdle()
                    h.next()
                    assertEquals(0, h.week())
                    h.previous()
                    advanceUntilIdle()
                    assertFalse(h.hasPrevious())
                    repeat(10) { h.previous() }
                    assertEquals(1, h.week())
                    h.next()
                    assertTrue(h.hasPrevious())
                    h.previous()
                    advanceUntilIdle()
                    assertEquals(1, h.week())
                    assertTrue(h.ready())
                }
            } finally {
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun dateNavigationFinishesWithoutForcedOneSecondLoading() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                run {
                    val h =
                        Harness {
                            delay(50)
                            true
                        }
                    advanceUntilIdle()
                    h.previous()
                    advanceTimeBy(199)
                    runCurrent()
                    assertFalse(h.ready())
                    advanceTimeBy(1)
                    runCurrent()
                    assertTrue(h.ready())
                    assertEquals(1, h.week())
                }
            } finally {
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun rapidNavigationNeverExceedsApiLimit() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val h = Harness(oldest = 520) { true }
                advanceUntilIdle()
                repeat(600) { h.previous() }
                assertEquals(520, h.week())
                assertFalse(h.hasPrevious())
                advanceUntilIdle()
                assertTrue(h.ready())
                h.next()
                advanceUntilIdle()
                assertEquals(519, h.week())
            } finally {
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    private class Harness(
        oldest: Int? = null,
        response: suspend (Int) -> Boolean,
    ) {
        private val personal =
            PressRankingViewModel(
                object : PressRankingSource {
                    override suspend fun load(
                        emotion: PressEmotion,
                        weeksAgo: Int,
                    ): PressRankingLoadResult =
                        if (response(weeksAgo)) {
                            PressRankingLoadResult.Loaded(
                                "week-$weeksAgo",
                                "end-$weeksAgo",
                                oldest == null || weeksAgo < oldest,
                                emptyList(),
                            )
                        } else {
                            PressRankingLoadResult.Unavailable
                        }
                },
            )

        fun previous() = personal.onPreviousWeek()

        fun next() = personal.onNextWeek()

        fun retry() = personal.onRetry()

        fun week() = personal.uiState.value.weeksAgo

        fun label() = personal.uiState.value.weekLabel

        fun hasPrevious() = personal.uiState.value.hasPrevious

        fun refreshing() = personal.uiState.value.isRefreshing

        fun ready() = personal.uiState.value.status == PressRankingStatus.Ready

        fun failed() = personal.uiState.value.status == PressRankingStatus.Failed
    }
}
