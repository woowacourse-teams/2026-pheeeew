package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {
    @Test
    fun `fast initial response does not show loading indicator`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = createViewModel(source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) })
                runCurrent()

                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isLoading)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `slow initial response shows spinner after delay and keeps it visible for minimum duration`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val response = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel = createViewModel(source = { response.await() })
                runCurrent()

                assertEquals(GroupDetailContent.Loading, viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
                advanceTimeBy(149)
                runCurrent()
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)

                advanceTimeBy(1)
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)
                response.complete(GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)))
                runCurrent()

                assertEquals(GroupDetailContent.Loading, viewModel.uiState.value.content)
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)
                advanceTimeBy(299)
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)

                advanceTimeBy(1)
                runCurrent()
                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `retry keeps error content and prevents duplicate request while spinner is delayed`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                val retryResponse = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel(source = {
                        reads++
                        if (reads == 1) GroupDetailLoadResult.Unavailable else retryResponse.await()
                    })
                runCurrent()
                assertEquals(GroupDetailContent.LoadFailed, viewModel.uiState.value.content)

                viewModel.onRetry()
                runCurrent()
                assertEquals(GroupDetailContent.LoadFailed, viewModel.uiState.value.content)
                assertTrue(viewModel.uiState.value.isLoading)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)

                viewModel.onRetry()
                runCurrent()
                assertEquals(2, reads)

                advanceTimeBy(150)
                runCurrent()
                assertEquals(GroupDetailContent.LoadFailed, viewModel.uiState.value.content)
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)
                retryResponse.complete(GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)))
                runCurrent()
                assertEquals(GroupDetailContent.LoadFailed, viewModel.uiState.value.content)

                advanceTimeBy(300)
                runCurrent()
                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isLoading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `leaving cancels pending refresh and its loading indicator`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var refreshCancelled = false
                val refreshResponse = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel(
                        source = {
                            reads++
                            if (reads == 1) {
                                GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER))
                            } else {
                                try {
                                    refreshResponse.await()
                                } catch (cancelled: CancellationException) {
                                    refreshCancelled = true
                                    throw cancelled
                                }
                            }
                        },
                        leave = { LeaveGroupResult.Left },
                    )
                runCurrent()
                viewModel.onRefresh()
                runCurrent()
                advanceTimeBy(150)
                runCurrent()
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)

                viewModel.onMoreClick()
                viewModel.onLeaveMenuClick()
                viewModel.onConfirmLeave()
                runCurrent()

                assertTrue(refreshCancelled)
                assertFalse(viewModel.uiState.value.isLoading)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
                assertIs<GroupDetailOverlay.Left>(viewModel.uiState.value.overlay)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `role none is a ready detail without member actions`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var leaveCalls = 0
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.NONE)) },
                        leave = {
                            leaveCalls++
                            LeaveGroupResult.Left
                        },
                    )
                runCurrent()

                assertEquals(
                    GroupRole.NONE,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                viewModel.onMoreClick()
                viewModel.onInviteClick()
                viewModel.onLeaveMenuClick()
                viewModel.onConfirmLeave()

                assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)
                assertEquals(0, leaveCalls)
                assertNull(viewModel.uiState.value.copyRequest)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `member leave completes without a press session`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var leaveCalls = 0
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) },
                        leave = {
                            leaveCalls++
                            LeaveGroupResult.Left
                        },
                    )
                runCurrent()
                viewModel.onMoreClick()
                viewModel.onLeaveMenuClick()
                viewModel.onConfirmLeave()
                runCurrent()

                assertEquals(1, leaveCalls)
                assertIs<GroupDetailOverlay.Left>(viewModel.uiState.value.overlay)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `resume refresh closes invite dialog and clears pending copy when role becomes none`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                val viewModel =
                    createViewModel(source = {
                        reads++
                        GroupDetailLoadResult.Loaded(detail(if (reads == 1) GroupRole.MEMBER else GroupRole.NONE))
                    })
                runCurrent()
                viewModel.onResumed()
                viewModel.onInviteClick()
                viewModel.onCopyCodeClick()
                assertEquals(GroupDetailOverlay.InviteCode, viewModel.uiState.value.overlay)
                assertEquals(
                    "ABCD1234",
                    viewModel.uiState.value.copyRequest
                        ?.code,
                )

                viewModel.onResumed()
                runCurrent()

                assertEquals(2, reads)
                assertEquals(
                    GroupRole.NONE,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)
                assertNull(viewModel.uiState.value.copyRequest)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `leave outcome reconciles none as completed and member as still joined`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads++
                            GroupDetailLoadResult.Loaded(detail(if (reads == 1) GroupRole.MEMBER else GroupRole.NONE))
                        },
                        leave = { LeaveGroupResult.OutcomeUnknown },
                    )
                runCurrent()
                viewModel.onMoreClick()
                viewModel.onLeaveMenuClick()
                viewModel.onConfirmLeave()
                runCurrent()
                assertEquals(GroupDetailOverlay.LeaveOutcomeUnknown, viewModel.uiState.value.overlay)

                viewModel.onResolveLeaveOutcome()
                runCurrent()

                assertEquals(
                    GroupRole.NONE,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                assertIs<GroupDetailOverlay.Left>(viewModel.uiState.value.overlay)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `leave outcome recheck keeps owner and member in the group`() =
        runTest {
            for (role in listOf(GroupRole.OWNER, GroupRole.MEMBER)) {
                Dispatchers.setMain(StandardTestDispatcher(testScheduler))
                try {
                    var reads = 0
                    val viewModel =
                        createViewModel(
                            source = {
                                reads++
                                GroupDetailLoadResult.Loaded(detail(if (reads == 1) GroupRole.MEMBER else role))
                            },
                            leave = { LeaveGroupResult.OutcomeUnknown },
                        )
                    runCurrent()
                    viewModel.onMoreClick()
                    viewModel.onLeaveMenuClick()
                    viewModel.onConfirmLeave()
                    runCurrent()
                    viewModel.onResolveLeaveOutcome()
                    runCurrent()

                    assertEquals(
                        role,
                        viewModel.uiState.value.detail
                            ?.role,
                    )
                    assertEquals(GroupDetailOverlay.LeaveStillMember, viewModel.uiState.value.overlay)
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }

    @Test
    fun `refresh failure retains ready content and reports refresh error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                val viewModel =
                    createViewModel(source = {
                        reads++
                        if (reads ==
                            1
                        ) {
                            GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER))
                        } else {
                            GroupDetailLoadResult.Unavailable
                        }
                    })
                runCurrent()
                viewModel.onRefresh()
                runCurrent()

                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertEquals(GroupDetailRefreshStatus.Failed, viewModel.uiState.value.refreshStatus)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        source: suspend () -> GroupDetailLoadResult,
        leave: suspend () -> LeaveGroupResult = { LeaveGroupResult.Unavailable },
    ) = GroupDetailViewModel(
        groupId = GROUP_ID,
        dependencies =
            GroupDetailDependencies(
                source = { source() },
                leaveGroupAction = { leave() },
                errorReporter = { throw it },
                operationKeyAllocator = GroupOperationKeyAllocator("detail-test"),
            ),
    )

    private fun detail(role: GroupRole) =
        GroupDetailUiModel(
            group =
                GroupSummaryUiModel(
                    id = GROUP_ID,
                    name = "한숨모임",
                    memberCount = 7L,
                    weeklyStampCount = null,
                    stamp = StampAppearanceUiModel("버티자", StampShapeId.FLOWER, 0xFF4A90D9, 0xFFFFFFFF),
                    description = "퇴근하고 한 번씩",
                ),
            role = role,
            inviteCode = "ABCD1234",
            weeklyStampCount = 42L,
            weeklyStampRank = 3,
            weeklyEmotionPressCount = 318L,
            weeklyEmotionPressRank = 5,
        )

    private companion object {
        val GROUP_ID = GroupId("0b8f3a2e-5c71-4d9a-b0e4-7f2c1a6d8e39")
    }
}
