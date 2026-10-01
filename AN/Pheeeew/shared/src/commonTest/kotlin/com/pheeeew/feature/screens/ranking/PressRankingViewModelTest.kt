package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
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
class PressRankingViewModelTest {
    @Test
    fun `선택한 감정과 주로 다시 요청하고 새로고침 실패 시 기존 목록을 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Pair<PressEmotion, Int>>()
                var shouldFail = false
                val groups = listOf(PressGroupRank(1, "히유 클럽", "내 그룹", 42, true))
                val source =
                    object : PressRankingSource {
                        override suspend fun load(
                            emotion: PressEmotion,
                            weeksAgo: Int,
                        ): PressRankingLoadResult {
                            calls += emotion to weeksAgo
                            if (shouldFail) return PressRankingLoadResult.Unavailable
                            return PressRankingLoadResult.Loaded(
                                startAt = "2026-09-21T00:00:00Z",
                                endAt = "2026-09-28T00:00:00Z",
                                hasPrevious = true,
                                groups = groups,
                            )
                        }
                    }
                val viewModel = PressRankingViewModel(source)
                runCurrent()
                advanceUntilIdle()
                assertEquals(PressRankingStatus.Ready, viewModel.uiState.value.status)

                viewModel.onEmotionSelected(PressEmotion.Angry)
                advanceUntilIdle()
                viewModel.onPreviousWeek()
                advanceUntilIdle()
                assertEquals(listOf(PressEmotion.All to 0, PressEmotion.Angry to 0, PressEmotion.Angry to 1), calls)
                assertEquals(PressEmotion.Angry, viewModel.uiState.value.selectedEmotion)
                assertEquals(1, viewModel.uiState.value.weeksAgo)
                assertEquals("2026.09.21 ~ 2026.09.28", viewModel.uiState.value.weekLabel)

                shouldFail = true
                viewModel.onRefresh()
                advanceUntilIdle()
                assertTrue(viewModel.uiState.value.hasRefreshError)
                assertEquals(groups, viewModel.uiState.value.groups)
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `빈 랭킹은 준비 완료 상태로 노출되고 초기 실패는 다시 시도할 수 있다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var unavailable = true
                val source =
                    object : PressRankingSource {
                        override suspend fun load(
                            emotion: PressEmotion,
                            weeksAgo: Int,
                        ): PressRankingLoadResult =
                            if (unavailable) {
                                PressRankingLoadResult.Unavailable
                            } else {
                                PressRankingLoadResult.Loaded(
                                    startAt = "2026-09-28T00:00:00Z",
                                    endAt = "2026-10-05T00:00:00Z",
                                    hasPrevious = false,
                                    groups = emptyList(),
                                )
                            }
                    }
                val viewModel = PressRankingViewModel(source)
                runCurrent()
                advanceUntilIdle()
                assertEquals(PressRankingStatus.Failed, viewModel.uiState.value.status)

                unavailable = false
                viewModel.onRetry()
                advanceUntilIdle()
                assertEquals(PressRankingStatus.Ready, viewModel.uiState.value.status)
                assertTrue(
                    viewModel.uiState.value.groups
                        .isEmpty(),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `늦게 도착한 이전 필터 응답은 현재 선택 결과를 덮지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val staleResponse = CompletableDeferred<PressRankingLoadResult>()
                val source =
                    object : PressRankingSource {
                        override suspend fun load(
                            emotion: PressEmotion,
                            weeksAgo: Int,
                        ): PressRankingLoadResult =
                            if (emotion == PressEmotion.All) {
                                withContext(NonCancellable) { staleResponse.await() }
                            } else {
                                PressRankingLoadResult.Loaded(
                                    startAt = "2026-09-28T00:00:00Z",
                                    endAt = "2026-10-05T00:00:00Z",
                                    hasPrevious = false,
                                    groups = listOf(PressGroupRank(1, "현재 결과", "", 20)),
                                )
                            }
                    }
                val viewModel = PressRankingViewModel(source)
                runCurrent()

                viewModel.onEmotionSelected(PressEmotion.Angry)
                advanceUntilIdle()
                assertEquals(
                    "현재 결과",
                    viewModel.uiState.value.groups
                        .single()
                        .groupName,
                )

                staleResponse.complete(
                    PressRankingLoadResult.Loaded(
                        startAt = "2026-09-21T00:00:00Z",
                        endAt = "2026-09-28T00:00:00Z",
                        hasPrevious = true,
                        groups = listOf(PressGroupRank(1, "늦은 결과", "", 99)),
                    ),
                )
                advanceUntilIdle()
                assertEquals(PressEmotion.Angry, viewModel.uiState.value.selectedEmotion)
                assertEquals(
                    "현재 결과",
                    viewModel.uiState.value.groups
                        .single()
                        .groupName,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }
}
