package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailEmotionRankingTest {
    @Test
    fun `loads the active group rank once and does not reload it for emotion taps`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                var rankingCalls = 0
                val viewModel =
                    createViewModel(
                        detail = detail,
                        ranking = { groupId ->
                            assertEquals(detail.group.id, groupId)
                            rankingCalls++
                            GroupDetailEmotionRankingResult.Ranked(rank = 3, score = 28)
                        },
                    )

                runCurrent()
                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 3, score = 28),
                    viewModel.uiState.value.emotionRanking.content,
                )
                assertEquals(1, rankingCalls)

                assertTrue(viewModel.onEmotionTap(EmotionKind.entries.first()))
                runCurrent()

                assertEquals(1, rankingCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `unavailable ranking can be retried independently`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                var calls = 0
                val viewModel =
                    createViewModel(
                        detail = detail,
                        ranking = {
                            calls++
                            if (calls == 1) {
                                GroupDetailEmotionRankingResult.Unavailable
                            } else {
                                GroupDetailEmotionRankingResult.Ranked(rank = 2, score = 7)
                            }
                        },
                    )

                runCurrent()
                assertEquals(
                    GroupDetailEmotionRankingContent.Unavailable,
                    viewModel.uiState.value.emotionRanking.content,
                )
                viewModel.onRetryEmotionRanking()
                runCurrent()

                assertEquals(2, calls)
                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 2, score = 7),
                    viewModel.uiState.value.emotionRanking.content,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `preserves the previous rank and shows refresh failure`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                val refreshRanking = CompletableDeferred<GroupDetailEmotionRankingResult>()
                var calls = 0
                val viewModel =
                    createViewModel(
                        detail = detail,
                        ranking = {
                            calls++
                            if (calls == 1) {
                                GroupDetailEmotionRankingResult.Ranked(rank = 4, score = 12)
                            } else {
                                refreshRanking.await()
                            }
                        },
                        detailSource = { GroupDetailLoadResult.Loaded(detail) },
                    )

                runCurrent()
                viewModel.onRetry()
                runCurrent()
                assertTrue(viewModel.uiState.value.emotionRanking.isRefreshing)
                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 4, score = 12),
                    viewModel.uiState.value.emotionRanking.content,
                )

                refreshRanking.complete(GroupDetailEmotionRankingResult.Unavailable)
                runCurrent()

                assertFalse(viewModel.uiState.value.emotionRanking.isRefreshing)
                assertTrue(viewModel.uiState.value.emotionRanking.hasRefreshError)
                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 4, score = 12),
                    viewModel.uiState.value.emotionRanking.content,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `preserves a zero score result when refresh fails`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                val refreshRanking = CompletableDeferred<GroupDetailEmotionRankingResult>()
                var calls = 0
                val viewModel =
                    createViewModel(
                        detail = detail,
                        ranking = {
                            calls++
                            if (calls == 1) {
                                GroupDetailEmotionRankingResult.NoPresses
                            } else {
                                refreshRanking.await()
                            }
                        },
                        detailSource = { GroupDetailLoadResult.Loaded(detail) },
                    )

                runCurrent()
                viewModel.onRetry()
                runCurrent()
                assertTrue(viewModel.uiState.value.emotionRanking.isRefreshing)

                refreshRanking.complete(GroupDetailEmotionRankingResult.Unavailable)
                runCurrent()

                assertFalse(viewModel.uiState.value.emotionRanking.isRefreshing)
                assertTrue(viewModel.uiState.value.emotionRanking.hasRefreshError)
                assertEquals(
                    GroupDetailEmotionRankingContent.NoPresses,
                    viewModel.uiState.value.emotionRanking.content,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `late ranking response cannot replace the result for a newer detail refresh`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                val firstRanking = CompletableDeferred<GroupDetailEmotionRankingResult>()
                var rankingCalls = 0
                val viewModel =
                    createViewModel(
                        detail = detail,
                        detailSource = { GroupDetailLoadResult.Loaded(detail) },
                        ranking = {
                            rankingCalls++
                            if (rankingCalls == 1) {
                                withContext(NonCancellable) { firstRanking.await() }
                            } else {
                                GroupDetailEmotionRankingResult.Ranked(rank = 2, score = 31)
                            }
                        },
                    )

                runCurrent()
                viewModel.onRetry()
                runCurrent()
                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 2, score = 31),
                    viewModel.uiState.value.emotionRanking.content,
                )

                firstRanking.complete(GroupDetailEmotionRankingResult.Ranked(rank = 9, score = 2))
                runCurrent()

                assertEquals(
                    GroupDetailEmotionRankingContent.Ranked(rank = 2, score = 31),
                    viewModel.uiState.value.emotionRanking.content,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        detail: com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel,
        ranking: suspend (com.pheeeew.feature.screens.group.model.GroupId) -> GroupDetailEmotionRankingResult,
        detailSource: GroupDetailSource = { GroupDetailLoadResult.Loaded(detail) },
    ): GroupDetailViewModel {
        val dependencies =
            GroupDetailDependencies(
                source = detailSource,
                pressGroupEmotionAction = { _, _ -> PressGroupEmotionResult.Unavailable },
                leaveGroupAction = { LeaveGroupResult.Unavailable },
                errorReporter = { throw it },
                operationKeyAllocator = GroupOperationKeyAllocator("emotion-ranking-test"),
                emotionRankingSource = GroupDetailEmotionRankingSource(ranking),
            )
        return GroupDetailViewModel(detail.group.id, dependencies)
    }
}
