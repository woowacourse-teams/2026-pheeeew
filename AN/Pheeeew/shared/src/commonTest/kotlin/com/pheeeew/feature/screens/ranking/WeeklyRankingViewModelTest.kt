package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingLoadResult
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingSource
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingStatus
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyRankingViewModelTest {
    @Test
    fun `refresh keeps current week and loading feedback while rejecting duplicate requests`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val requestedWeeks = mutableListOf<Int>()
                val source =
                    object : WeeklyRankingSource {
                        override suspend fun load(weeksAgo: Int): WeeklyRankingLoadResult {
                            requestedWeeks += weeksAgo
                            return WeeklyRankingLoadResult.Loaded(
                                startAt = "2026-09-21T00:00:00Z",
                                endAt = "2026-09-28T00:00:00Z",
                                hasPrevious = true,
                                rankings = emptyList(),
                            )
                        }
                    }
                val viewModel = WeeklyRankingViewModel(source)
                runCurrent()
                assertEquals(WeeklyRankingStatus.Loading, viewModel.uiState.value.status)
                advanceUntilIdle()
                viewModel.onPreviousWeek()
                advanceUntilIdle()
                val previous = viewModel.uiState.value

                viewModel.onRefresh()
                runCurrent()
                advanceTimeBy(999)
                runCurrent()
                assertTrue(viewModel.uiState.value.isRefreshing)
                assertEquals(previous.weeksAgo, viewModel.uiState.value.weeksAgo)
                assertEquals(previous.rankings, viewModel.uiState.value.rankings)
                assertEquals(WeeklyRankingStatus.Ready, viewModel.uiState.value.status)
                viewModel.onRefresh()
                assertEquals(listOf(0, 1, 1), requestedWeeks)

                advanceTimeBy(1)
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
                assertEquals(1, viewModel.uiState.value.weeksAgo)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
