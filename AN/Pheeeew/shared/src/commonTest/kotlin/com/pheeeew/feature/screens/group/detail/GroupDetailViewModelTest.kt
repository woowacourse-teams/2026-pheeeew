package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.repository.EmotionFailure
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.EmotionResult
import com.pheeeew.domain.repository.GroupPublicJoinResult
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {
    @Test
    fun `unavailable public join leaves membership unchanged`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel =
                    createViewModel(
                        source = {
                            GroupDetailLoadResult.Loaded(
                                detail(GroupRole.NONE).copy(joinType = GroupJoinType.PUBLIC),
                            )
                        },
                    )
                runCurrent()

                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()

                val loadedDetail = requireNotNull(viewModel.uiState.value.detail)
                assertEquals(GroupRole.NONE, loadedDetail.role)
                assertEquals(7L, loadedDetail.group.memberCount)
                assertEquals(GroupDetailJoinFailure.Unavailable, viewModel.uiState.value.join.failure)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `private guest cannot start direct join from group detail`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.NONE)) },
                    )
                runCurrent()

                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)
                assertNull(viewModel.uiState.value.join.failure)
                val loadedDetail = requireNotNull(viewModel.uiState.value.detail)
                assertEquals(GroupRole.NONE, loadedDetail.role)
                assertEquals(7L, loadedDetail.group.memberCount)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `private guest can view and refresh the mood feed`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var feedRequests = 0
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            feedRequests++
                            return EmotionResult.Success(EmotionPage(emptyList(), null))
                        }
                    }
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.NONE)) },
                        emotionRepository = repository,
                    )
                runCurrent()
                assertEquals(1, feedRequests)
                assertIs<GroupMoodFeedUiState.Available>(viewModel.uiState.value.moodFeed)

                viewModel.onRefresh()
                runCurrent()

                assertEquals(2, feedRequests)
                assertIs<GroupMoodFeedUiState.Available>(viewModel.uiState.value.moodFeed)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `initial feed failure leaves group detail ready and shows feed error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> = EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
                    }
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) },
                        emotionRepository = repository,
                    )

                runCurrent()

                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertEquals(GroupMoodFeedUiState.LoadFailed(isRetrying = false), viewModel.uiState.value.moodFeed)
                assertFalse(viewModel.uiState.value.isLoading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh keeps one global loading phase until detail and feed finish`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var detailRequests = 0
                var feedRequests = 0
                val detailRefresh = CompletableDeferred<GroupDetailLoadResult>()
                val feedRefresh = CompletableDeferred<EmotionResult<EmotionPage>>()
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            feedRequests++
                            return if (feedRequests == 1) {
                                EmotionResult.Success(EmotionPage(emptyList(), null))
                            } else {
                                feedRefresh.await()
                            }
                        }
                    }
                val viewModel =
                    createViewModel(
                        source = {
                            detailRequests++
                            if (detailRequests == 1) {
                                GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER))
                            } else {
                                detailRefresh.await()
                            }
                        },
                        emotionRepository = repository,
                    )
                runCurrent()

                viewModel.onRefresh()
                runCurrent()

                assertTrue(viewModel.uiState.value.isLoading)
                assertTrue(viewModel.uiState.value.isRefreshing)
                assertEquals(
                    GroupMoodFeedLoadState.Refreshing,
                    (viewModel.uiState.value.moodFeed as GroupMoodFeedUiState.Available).loadState,
                )
                viewModel.onRefresh()
                runCurrent()
                assertEquals(2, detailRequests)
                assertEquals(2, feedRequests)

                detailRefresh.complete(GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)))
                runCurrent()

                assertTrue(viewModel.uiState.value.isLoading)
                assertTrue(viewModel.uiState.value.isRefreshing)

                feedRefresh.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                runCurrent()

                assertFalse(viewModel.uiState.value.isLoading)
                assertFalse(viewModel.uiState.value.isRefreshing)
                assertEquals(GroupDetailRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)
                assertEquals(2, detailRequests)
                assertEquals(2, feedRequests)
            } finally {
                Dispatchers.resetMain()
            }
        }

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
    fun `slow initial detail and feed share one loading phase`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val response = CompletableDeferred<GroupDetailLoadResult>()
                val feedResponse = CompletableDeferred<EmotionResult<EmotionPage>>()
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> = feedResponse.await()
                    }
                val viewModel = createViewModel(source = { response.await() }, emotionRepository = repository)
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
                assertTrue(viewModel.uiState.value.isLoading)
                assertTrue(viewModel.uiState.value.isLoadingIndicatorVisible)

                feedResponse.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                runCurrent()

                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertIs<GroupMoodFeedUiState.Available>(viewModel.uiState.value.moodFeed)
                assertFalse(viewModel.uiState.value.isLoading)
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
                var feedRequests = 0
                var feedRefreshCancelled = false
                val refreshResponse = CompletableDeferred<GroupDetailLoadResult>()
                val feedRefreshResponse = CompletableDeferred<EmotionResult<EmotionPage>>()
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            feedRequests++
                            if (feedRequests == 1) return EmotionResult.Success(EmotionPage(emptyList(), null))
                            return try {
                                feedRefreshResponse.await()
                            } catch (cancelled: CancellationException) {
                                feedRefreshCancelled = true
                                throw cancelled
                            }
                        }
                    }
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
                        emotionRepository = repository,
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
                assertTrue(feedRefreshCancelled)
                assertFalse(viewModel.uiState.value.isLoading)
                assertFalse(viewModel.uiState.value.isLoadingIndicatorVisible)
                assertEquals(
                    GroupMoodFeedLoadState.Idle,
                    (viewModel.uiState.value.moodFeed as GroupMoodFeedUiState.Available).loadState,
                )
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

    @Test
    fun `feed refresh failure retains the previous posts`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var feedRequests = 0
                val originalPost = emotion(id = 42L)
                val repository =
                    object : EmotionRepository by TestEmotionRepository {
                        override suspend fun feedPage(
                            groupId: String,
                            cursor: String?,
                        ): EmotionResult<EmotionPage> {
                            feedRequests++
                            return if (feedRequests == 1) {
                                EmotionResult.Success(EmotionPage(listOf(originalPost), "next"))
                            } else {
                                EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
                            }
                        }
                    }
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER)) },
                        emotionRepository = repository,
                    )
                runCurrent()

                val originalDetail = viewModel.uiState.value.detail
                viewModel.onRefresh()
                runCurrent()

                assertEquals(originalDetail, viewModel.uiState.value.detail)
                val refreshedFeed = assertIs<GroupMoodFeedUiState.Available>(viewModel.uiState.value.moodFeed)
                assertEquals(listOf("42"), refreshedFeed.posts.map { it.id })
                assertEquals(GroupMoodFeedLoadState.RefreshFailed(isRetrying = false), refreshedFeed.loadState)
                assertTrue(refreshedFeed.hasMore)
                assertEquals(GroupDetailRefreshStatus.Idle, viewModel.uiState.value.refreshStatus)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `public join writes once and completes only after server membership confirmation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var writes = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads += 1
                            GroupDetailLoadResult.Loaded(
                                detail(if (reads == 1) GroupRole.NONE else GroupRole.MEMBER)
                                    .copy(joinType = GroupJoinType.PUBLIC),
                            )
                        },
                        joinAction =
                            JoinPublicGroupAction {
                                writes += 1
                                GroupPublicJoinResult.Joined
                            },
                    )
                runCurrent()
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(1, writes)
                assertEquals(2, reads)
                assertEquals(
                    GroupRole.MEMBER,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                assertEquals(GroupDetailJoinPhase.Completed, viewModel.uiState.value.join.phase)
                assertTrue(viewModel.uiState.value.joinConfirmed != null)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed confirmation retries only GET and never repeats a membership write`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var writes = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads += 1
                            if (reads == 2) {
                                GroupDetailLoadResult.Unavailable
                            } else {
                                GroupDetailLoadResult.Loaded(
                                    detail(
                                        if (reads ==
                                            1
                                        ) {
                                            GroupRole.NONE
                                        } else {
                                            GroupRole.MEMBER
                                        },
                                    ).copy(joinType = GroupJoinType.PUBLIC),
                                )
                            }
                        },
                        joinAction =
                            JoinPublicGroupAction {
                                writes += 1
                                GroupPublicJoinResult.OutcomeUnknown
                            },
                    )
                runCurrent()
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(GroupDetailJoinPhase.VerificationRequired, viewModel.uiState.value.join.phase)
                assertEquals(
                    GroupRole.NONE,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(1, writes)
                assertEquals(3, reads)
                assertEquals(GroupDetailJoinPhase.Completed, viewModel.uiState.value.join.phase)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `private conflict refreshes policy without fabricating joined membership`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads += 1
                            GroupDetailLoadResult.Loaded(
                                detail(GroupRole.NONE)
                                    .copy(joinType = if (reads == 1) GroupJoinType.PUBLIC else GroupJoinType.PRIVATE),
                            )
                        },
                        joinAction =
                            JoinPublicGroupAction {
                                GroupPublicJoinResult.PrivateGroup
                            },
                    )
                runCurrent()
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(
                    GroupJoinType.PRIVATE,
                    viewModel.uiState.value.detail
                        ?.joinType,
                )
                assertEquals(
                    GroupRole.NONE,
                    viewModel.uiState.value.detail
                        ?.role,
                )
                assertEquals(GroupDetailJoinFailure.PrivateGroup, viewModel.uiState.value.join.failure)
                assertNull(viewModel.uiState.value.joinConfirmed)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `already joined and private conflicts both trust confirmed server membership`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                listOf(GroupPublicJoinResult.AlreadyMember, GroupPublicJoinResult.PrivateGroup).forEach { outcome ->
                    var reads = 0
                    val viewModel =
                        createViewModel(
                            source = {
                                reads += 1
                                val server = detail(if (reads == 1) GroupRole.NONE else GroupRole.MEMBER)
                                GroupDetailLoadResult.Loaded(
                                    server.copy(
                                        joinType = if (reads == 1) GroupJoinType.PUBLIC else GroupJoinType.PRIVATE,
                                        group = server.group.copy(memberCount = if (reads == 1) 7 else 19),
                                    ),
                                )
                            },
                            joinAction = JoinPublicGroupAction { outcome },
                        )
                    runCurrent()
                    viewModel.onJoinClick()
                    viewModel.onConfirmJoin()
                    runCurrent()
                    assertEquals(
                        GroupRole.MEMBER,
                        viewModel.uiState.value.detail
                            ?.role,
                    )
                    assertEquals(
                        19L,
                        viewModel.uiState.value.detail
                            ?.group
                            ?.memberCount,
                    )
                    assertEquals(GroupDetailJoinPhase.Completed, viewModel.uiState.value.join.phase)
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `closing an uncertain code join requires GET before another membership attempt`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var writes = 0
                val codeDependencies =
                    com.pheeeew.feature.screens.group.join.GroupJoinDependencies(
                        lookupGroupAction = {
                            com.pheeeew.feature.screens.group.join.GroupLookupResult.Found(
                                detail(GroupRole.NONE).group,
                            )
                        },
                        joinGroupAction = { _, _ ->
                            writes += 1
                            com.pheeeew.feature.screens.group.join.GroupJoinResult.OutcomeUnknown
                        },
                        errorReporter = { throw it },
                        operationKeyAllocator = GroupOperationKeyAllocator("code-detail-test"),
                    )
                val viewModel =
                    createViewModel(
                        source = {
                            reads += 1
                            GroupDetailLoadResult.Loaded(detail(if (reads == 1) GroupRole.NONE else GroupRole.MEMBER))
                        },
                        codeJoin = codeDependencies,
                    )
                runCurrent()
                viewModel.onJoinClick()
                assertTrue(viewModel.uiState.value.isJoinConfirmationVisible)
                assertFalse(viewModel.isCodeJoinVisible.value)
                assertEquals(0, writes)
                viewModel.onCancelJoin()
                assertFalse(viewModel.isCodeJoinVisible.value)
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                assertTrue(viewModel.isCodeJoinVisible.value)
                assertFalse(viewModel.uiState.value.isJoinConfirmationVisible)
                viewModel.onJoinCodeChanged("ABC123")
                viewModel.onJoinCodeSearch()
                runCurrent()
                viewModel.onJoinCodeSubmit()
                runCurrent()
                viewModel.onCloseCodeJoin()
                assertEquals(GroupDetailJoinPhase.VerificationRequired, viewModel.uiState.value.join.phase)
                assertEquals("ABC123", viewModel.codeJoinUiState.value.input)
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(1, writes)
                assertEquals(2, reads)
                assertEquals(GroupDetailJoinPhase.Completed, viewModel.uiState.value.join.phase)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `public join requires confirmation and cancel sends no write`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var writes = 0
                val viewModel =
                    createViewModel(
                        source = {
                            GroupDetailLoadResult.Loaded(
                                detail(GroupRole.NONE).copy(joinType = GroupJoinType.PUBLIC),
                            )
                        },
                        joinAction =
                            JoinPublicGroupAction {
                                writes++
                                GroupPublicJoinResult.Unavailable
                            },
                    )
                runCurrent()
                viewModel.onConfirmJoin()
                viewModel.onJoinClick()
                viewModel.onJoinClick()
                runCurrent()
                assertTrue(viewModel.uiState.value.isJoinConfirmationVisible)
                assertEquals(0, writes)
                assertFalse(viewModel.onBackRequested())
                assertFalse(viewModel.uiState.value.isJoinConfirmationVisible)
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(0, writes)
                viewModel.onJoinClick()
                viewModel.onCancelJoin()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(0, writes)
                viewModel.onJoinClick()
                viewModel.onConfirmJoin()
                viewModel.onConfirmJoin()
                runCurrent()
                assertEquals(1, writes)
                assertFalse(viewModel.uiState.value.isJoinConfirmationVisible)
                viewModel.onJoinClick()
                assertTrue(viewModel.uiState.value.isJoinConfirmationVisible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun detailLoadingProbe() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (latency in listOf(0L, 50L, 200L, 1500L)) {
                    repeat(3) { repetition ->
                        var reads = 0
                        var feedReads = 0
                        var feedStart = -1L
                        val start = testScheduler.currentTime
                        val repo =
                            object : EmotionRepository by TestEmotionRepository {
                                override suspend fun feedPage(
                                    groupId: String,
                                    cursor: String?,
                                ): EmotionResult<EmotionPage> {
                                    feedReads++
                                    feedStart = testScheduler.currentTime - start
                                    return EmotionResult.Success(EmotionPage(emptyList(), null))
                                }
                            }
                        val vm =
                            createViewModel(source = {
                                reads++
                                delay(latency)
                                GroupDetailLoadResult.Loaded(detail(GroupRole.MEMBER))
                            }, emotionRepository = repo)
                        advanceUntilIdle()
                        assertIs<GroupDetailContent.Ready>(vm.uiState.value.content)
                        assertEquals(latency, testScheduler.currentTime - start)
                        assertEquals(latency, feedStart)
                        assertEquals(1, reads)
                        assertEquals(1, feedReads)
                        println(
                            "LOADING_PROBE,group_detail_initial,$latency,$repetition,${testScheduler.currentTime - start},$reads",
                        )
                        println("DETAIL_FEED_PROBE,$latency,$repetition,$feedStart,$feedReads")
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        source: suspend () -> GroupDetailLoadResult,
        leave: suspend () -> LeaveGroupResult = { LeaveGroupResult.Unavailable },
        emotionRepository: EmotionRepository = TestEmotionRepository,
        codeJoin: com.pheeeew.feature.screens.group.join.GroupJoinDependencies? = null,
        joinAction: JoinPublicGroupAction =
            JoinPublicGroupAction {
                GroupPublicJoinResult.Unavailable
            },
    ) = GroupDetailViewModel(
        groupId = GROUP_ID,
        dependencies =
            GroupDetailDependencies(
                source = { source() },
                publicJoinAction = joinAction,
                codeJoin = codeJoin,
                emotionRepository = emotionRepository,
                leaveGroupAction = { leave() },
                errorReporter = { throw it },
                operationKeyAllocator = GroupOperationKeyAllocator("detail-test"),
            ),
    )

    private fun emotion(id: Long) =
        Emotion(
            id = id,
            state = EmotionState.ANGRY,
            nickname = "member",
            createdAt = Clock.System.now(),
            isMine = false,
            contentType = EmotionContentType.MEMO,
            memo = "mood",
            reactions = emptyList(),
            groupStamp = null,
            audio = null,
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
            joinType = com.pheeeew.feature.screens.group.model.GroupJoinType.PRIVATE,
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

        override suspend fun delete(emotionId: Long) = EmotionModerationResult.Unavailable

        private fun <T> unavailable() = EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
    }
}
