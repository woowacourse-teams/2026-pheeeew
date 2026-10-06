package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailWeeklyPressCountViewModelTest {
    @Test
    fun `주간 집계를 표시하고 서버가 수락한 입력을 즉시 합산한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                val viewModel =
                    GroupDetailViewModel(
                        groupId = detail.group.id,
                        dependencies =
                            GroupDetailDependencies(
                                source = { GroupDetailLoadResult.Loaded(detail) },
                                pressGroupEmotionAction = { _, _ ->
                                    PressGroupEmotionResult.Pressed(
                                        GroupPressSnapshotUiModel(
                                            detail.emotionCounts,
                                            total = 1L,
                                        ),
                                    )
                                },
                                leaveGroupAction = { LeaveGroupResult.Unavailable },
                                errorReporter = { throw it },
                                operationKeyAllocator = GroupOperationKeyAllocator("weekly-count-test"),
                                weeklyPressCountSource =
                                    GroupDetailWeeklyPressCountSource {
                                        GroupDetailWeeklyPressCountResult.Loaded(7L)
                                    },
                            ),
                    )

                runCurrent()
                var weeklyState = viewModel.uiState.value
                assertEquals(7L, weeklyState.weeklyPressCount.displayedTotal)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                weeklyState = viewModel.uiState.value
                assertEquals(1L, weeklyState.pendingEmotionPresses.values.sum())
                runCurrent()

                weeklyState = viewModel.uiState.value
                assertEquals(8L, weeklyState.weeklyPressCount.displayedTotal)
                assertEquals(0L, weeklyState.pendingEmotionPresses.values.sum())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `미확정 입력은 주간 합계에 더하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
                val viewModel =
                    GroupDetailViewModel(
                        groupId = detail.group.id,
                        dependencies =
                            GroupDetailDependencies(
                                source = { GroupDetailLoadResult.Loaded(detail) },
                                pressGroupEmotionAction = { _, _ -> PressGroupEmotionResult.OutcomeUnknown },
                                leaveGroupAction = { LeaveGroupResult.Unavailable },
                                errorReporter = { throw it },
                                operationKeyAllocator = GroupOperationKeyAllocator("weekly-unknown-test"),
                                weeklyPressCountSource =
                                    GroupDetailWeeklyPressCountSource {
                                        GroupDetailWeeklyPressCountResult.Loaded(7L)
                                    },
                            ),
                    )

                runCurrent()
                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                runCurrent()

                val state = viewModel.uiState.value
                assertEquals(7L, state.weeklyPressCount.displayedTotal)
                assertEquals(0L, state.pendingEmotionPresses.values.sum())
                assertEquals(1, state.unconfirmedEmotionPresses.size)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
