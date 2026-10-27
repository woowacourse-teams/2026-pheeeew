package com.pheeeew.feature.screens.ranking

import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import com.pheeeew.domain.repository.DailyPressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import com.pheeeew.feature.screens.ranking.press.DailyPressRankingViewModel
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DailyPressRankingViewModelTest {
    @Test
    fun `자동 복귀 조회는 표시 없이 갱신하고 수동 조회만 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var pending: CompletableDeferred<DailyPressRankingLoadResult>? = null
                val result =
                    DailyPressRankingLoadResult.Loaded(
                        DailyPressRanking(
                            "2026-10-21",
                            "2026-10-20T15:00:00Z",
                            "2026-10-21T15:00:00Z",
                            emptyList(),
                            null,
                        ),
                    )
                val repository = RankingRepository { _, _ -> pending?.await() ?: result }
                val vm = DailyPressRankingViewModel(repository, "2026-10-21", 7)
                runCurrent()
                pending = CompletableDeferred()
                vm.onResumed()
                runCurrent()
                kotlin.test.assertFalse(vm.uiState.value.isRefreshing)
                pending.complete(result)
                runCurrent()
                pending = CompletableDeferred()
                vm.onRefresh()
                runCurrent()
                assertTrue(vm.uiState.value.isRefreshing)
                pending.complete(result)
                runCurrent()
                kotlin.test.assertFalse(vm.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `즉시 취소되는 세션 제거는 대기 알림 조회를 시작하지 않는다`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val pending = CompletableDeferred<DailyPressRankingLoadResult>()
                val repository = RankingRepository { _, _ -> pending.await() }
                val vm = DailyPressRankingViewModel(repository, "2026-10-21", 1)
                vm.onNotificationReceived()
                vm.onSessionChanged(null)
                vm.onRefresh()
                vm.onNotificationReceived()
                vm.onRetry()
                runCurrent()
                assertEquals(listOf(1L), repository.calls.map { it.second })
                assertEquals(PressRankingStatus.Failed, vm.uiState.value.status)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `즉시 취소되는 세션 교체는 새 세대로 한 번만 조회한다`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val pending = CompletableDeferred<DailyPressRankingLoadResult>()
                val repository =
                    RankingRepository { date, generation ->
                        if (generation == 1L) {
                            pending.await()
                        } else {
                            DailyPressRankingLoadResult.Loaded(
                                DailyPressRanking(
                                    date,
                                    "2026-10-20T15:00:00Z",
                                    "2026-10-21T15:00:00Z",
                                    emptyList(),
                                    null,
                                ),
                            )
                        }
                    }
                val vm = DailyPressRankingViewModel(repository, "2026-10-21", 1)
                vm.onNotificationReceived()
                vm.onSessionChanged(2)
                runCurrent()
                assertEquals(listOf(1L, 2L), repository.calls.map { it.second })
                assertEquals(PressRankingStatus.Ready, vm.uiState.value.status)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `일별 빈 결과와 100위 밖 내 순위를 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository =
                    RankingRepository { _, _ ->
                        DailyPressRankingLoadResult.Loaded(
                            DailyPressRanking(
                                "2026-10-21",
                                "2026-10-20T15:00:00Z",
                                "2026-10-21T15:00:00Z",
                                emptyList(),
                                PressRankingItem(150, "나", 3),
                            ),
                        )
                    }
                val viewModel = DailyPressRankingViewModel(repository, "2026-10-21", 7)
                advanceUntilIdle()

                assertEquals(PressRankingStatus.Ready, viewModel.uiState.value.status)
                assertEquals("2026-10-21", viewModel.uiState.value.date)
                assertTrue(
                    viewModel.uiState.value.people
                        .isEmpty(),
                )
                assertEquals(
                    150L,
                    viewModel.uiState.value.me
                        ?.rank,
                )
                assertEquals(1, repository.calls.size)
                assertEquals("2026-10-21", repository.calls.single().first)
                assertEquals(7L, repository.calls.single().second)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `세션이 바뀌면 이전 세션 응답을 화면에 적용하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val stale =
                    CompletableDeferred<DailyPressRankingLoadResult>()
                val repository =
                    RankingRepository { date, generation ->
                        if (generation == 1L) {
                            withContext(NonCancellable) { stale.await() }
                        } else {
                            DailyPressRankingLoadResult.Loaded(
                                DailyPressRanking(
                                    date,
                                    "2026-10-20T15:00:00Z",
                                    "2026-10-21T15:00:00Z",
                                    emptyList(),
                                    null,
                                ),
                            )
                        }
                    }
                val viewModel = DailyPressRankingViewModel(repository, "2026-10-21", 1)
                runCurrent()
                viewModel.onSessionChanged(2)
                advanceUntilIdle()
                stale.complete(
                    DailyPressRankingLoadResult.Loaded(
                        DailyPressRanking(
                            "2026-10-21",
                            "2026-10-20T15:00:00Z",
                            "2026-10-21T15:00:00Z",
                            listOf(PressRankingItem(1, "이전 세션", 99)),
                            null,
                        ),
                    ),
                )
                advanceUntilIdle()

                assertEquals(PressRankingStatus.Ready, viewModel.uiState.value.status)
                assertTrue(
                    viewModel.uiState.value.people
                        .isEmpty(),
                )
                assertEquals(2, repository.calls.size)
                assertEquals(listOf(1L, 2L), repository.calls.map { it.second })
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class RankingRepository(
        private val result: suspend (String, Long?) -> DailyPressRankingLoadResult,
    ) : PressRankingRepository {
        val calls = mutableListOf<Pair<String, Long?>>()

        override suspend fun findDaily(
            date: String,
            expectedSessionGeneration: Long?,
        ): DailyPressRankingLoadResult {
            calls += date to expectedSessionGeneration
            return result(date, expectedSessionGeneration)
        }

        override suspend fun find(
            weeksAgo: Int,
            state: String?,
        ): PressRankingLoadResult = PressRankingLoadResult.Unavailable
    }
}
