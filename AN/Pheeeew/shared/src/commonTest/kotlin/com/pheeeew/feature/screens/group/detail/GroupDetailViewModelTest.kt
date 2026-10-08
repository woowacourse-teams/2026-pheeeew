package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
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
    fun `invite share failure dismisses member popup and displays notice`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = createViewModel(source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) })
                runCurrent()
                viewModel.onInviteClick()
                viewModel.onInviteShareUnavailable()

                assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)
                assertEquals(
                    GroupDetailNoticeKind.InviteShareUnavailable,
                    viewModel.uiState.value.notice
                        ?.kind,
                )
                assertNull(viewModel.uiState.value.copyRequest)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `invite share failure is ignored for nonmember and closed popup`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (role in GroupRole.entries) {
                    val viewModel = createViewModel(source = { GroupDetailLoadResult.Loaded(detail(role)) })
                    runCurrent()
                    viewModel.onInviteShareUnavailable()
                    assertNull(viewModel.uiState.value.notice)
                    if (role == GroupRole.NONE) {
                        viewModel.onInviteClick()
                        viewModel.onInviteShareUnavailable()
                        assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)
                        assertNull(viewModel.uiState.value.notice)
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

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
    fun `그룹 감정 목록 다음 페이지 요청에 같은 그룹과 커서를 전달한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val requests = mutableListOf<Pair<String, String?>>()
                val emotionRepository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            requests += groupId to cursor
                            return EmotionResult.Success(
                                EmotionPage(
                                    items = emptyList(),
                                    nextCursor = if (cursor == null) "opaque-cursor" else null,
                                ),
                            )
                        }
                    }
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) },
                        emotionRepository = emotionRepository,
                    )
                runCurrent()

                viewModel.onMoodFeedLoadMore()
                runCurrent()

                assertEquals(
                    listOf(GROUP_ID.value to null, GROUP_ID.value to "opaque-cursor"),
                    requests,
                )
                assertFalse((viewModel.uiState.value.moodFeed as GroupMoodFeedUiState.Available).hasMore)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `초기 목록 오류에서 새로고침을 시작해도 오류 화면을 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val refreshFeed = CompletableDeferred<EmotionResult<EmotionPage>>()
                var feedRequests = 0
                val emotionRepository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            feedRequests++
                            return if (feedRequests == 1) {
                                EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
                            } else {
                                refreshFeed.await()
                            }
                        }
                    }
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) },
                        emotionRepository = emotionRepository,
                    )
                runCurrent()
                assertEquals(GroupMoodFeedUiState.LoadFailed(isRetrying = false), viewModel.uiState.value.moodFeed)

                viewModel.onRefresh()
                runCurrent()

                assertEquals(GroupMoodFeedUiState.LoadFailed(isRetrying = true), viewModel.uiState.value.moodFeed)
                refreshFeed.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                runCurrent()
                assertEquals(
                    GroupMoodFeedUiState.Available(
                        posts = emptyList(),
                        hasMore = false,
                        loadState = GroupMoodFeedLoadState.Idle,
                    ),
                    viewModel.uiState.value.moodFeed,
                )
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
        emotionRepository: EmotionRepository = TestEmotionRepository,
    ) = GroupDetailViewModel(
        groupId = GROUP_ID,
        dependencies =
            GroupDetailDependencies(
                source = { source() },
                emotionRepository = emotionRepository,
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

    private object TestEmotionRepository : EmotionRepository {
        override suspend fun firstPage(
            bounds: EmotionBounds,
            groupId: String?,
        ) = unavailable<EmotionPage>()

        override suspend fun nextPage(cursor: String) = unavailable<EmotionPage>()

        override suspend fun feedPage(
            groupId: String,
            cursor: String?,
        ) = EmotionResult.Success(EmotionPage(emptyList(), null))

        override suspend fun detail(id: Long): EmotionResult<Emotion> = unavailable<Emotion>()

        override suspend fun react(
            id: Long,
            type: EmotionReactionType,
            selected: Boolean,
        ) = unavailable<Unit>()

        override suspend fun block(id: Long) = unavailable<Unit>()

        private fun <T> unavailable() = EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
    }
}
