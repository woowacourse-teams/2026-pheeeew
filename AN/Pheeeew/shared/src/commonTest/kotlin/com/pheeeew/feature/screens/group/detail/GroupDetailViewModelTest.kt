package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModelTest {
    private val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)

    @Test
    fun `rapid taps are accepted immediately and sent one at a time in order`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val responses = List(3) { CompletableDeferred<PressGroupEmotionResult>() }
                val sent = mutableListOf<EmotionKind>()
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail) },
                        press = { _, emotion ->
                            sent += emotion
                            responses[sent.lastIndex].await()
                        },
                    )
                runCurrent()
                val emotions = EmotionKind.entries.take(3)
                emotions.forEach { assertTrue(viewModel.onEmotionTap(it)) }
                assertEquals(emotions.associateWith { 1L }, viewModel.uiState.value.pendingEmotionPresses)
                runCurrent()
                assertEquals(listOf(emotions[0]), sent)

                responses[0].complete(pressed(1))
                runCurrent()
                assertEquals(emotions.take(2), sent)
                assertEquals(
                    2L,
                    viewModel.uiState.value.pendingEmotionPresses.values
                        .sum(),
                )
                responses[1].complete(pressed(2))
                runCurrent()
                assertEquals(emotions, sent)
                responses[2].complete(pressed(3))
                runCurrent()
                assertEquals(GroupPressStatus.Idle, viewModel.uiState.value.pressStatus)
                assertTrue(
                    viewModel.uiState.value.pendingEmotionPresses
                        .isEmpty(),
                )
                assertEquals(
                    3L,
                    viewModel.uiState.value.detail
                        ?.todayTotal,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `server snapshot and remaining same emotion taps are combined once`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val first = CompletableDeferred<PressGroupEmotionResult>()
                val later = CompletableDeferred<PressGroupEmotionResult>()
                var calls = 0
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail) },
                        press = { _, _ ->
                            calls++
                            if (calls == 1) first.await() else later.await()
                        },
                    )
                runCurrent()
                val emotion = EmotionKind.entries.first()
                repeat(3) { assertTrue(viewModel.onEmotionTap(emotion)) }
                runCurrent()
                first.complete(pressed(1))
                runCurrent()
                val state = viewModel.uiState.value
                assertEquals(1L, state.detail?.todayTotal)
                assertEquals(2L, state.pendingEmotionPresses[emotion])
                assertEquals(3L, (state.detail?.todayTotal ?: 0L) + (state.pendingEmotionPresses[emotion] ?: 0L))
                later.complete(pressed(2))
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `leaving is blocked while press is active and while its result is unknown`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val response = CompletableDeferred<PressGroupEmotionResult>()
                var leaveCalls = 0
                val viewModel =
                    GroupDetailViewModel(
                        groupId = detail.group.id,
                        dependencies =
                            GroupDetailDependencies(
                                source = { GroupDetailLoadResult.Loaded(detail) },
                                pressGroupEmotionAction = { _, _ -> response.await() },
                                leaveGroupAction = {
                                    leaveCalls++
                                    LeaveGroupResult.Left
                                },
                                errorReporter = { throw it },
                                operationKeyAllocator = GroupOperationKeyAllocator("leave-guard-test"),
                                requestPolicy = GroupDetailRequestPolicy(),
                            ),
                    )
                runCurrent()
                assertTrue(viewModel.onEmotionTap(EmotionKind.entries.first()))
                runCurrent()
                viewModel.onMoreClick()
                viewModel.onLeaveMenuClick()
                assertEquals(0, leaveCalls)
                assertEquals(GroupDetailOverlay.None, viewModel.uiState.value.overlay)

                response.complete(PressGroupEmotionResult.OutcomeUnknown)
                runCurrent()
                viewModel.onMoreClick()
                viewModel.onLeaveMenuClick()
                assertEquals(0, leaveCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `unknown result blocks queued taps and reconciles by read without replay`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var posts = 0
                val reconcile = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel(
                        source = {
                            reads++
                            if (reads == 1) GroupDetailLoadResult.Loaded(detail) else reconcile.await()
                        },
                        press = { _, _ ->
                            posts++
                            if (posts == 1) PressGroupEmotionResult.OutcomeUnknown else pressed(2)
                        },
                    )
                runCurrent()
                val emotion = EmotionKind.entries.first()
                assertTrue(viewModel.onEmotionTap(emotion))
                assertTrue(viewModel.onEmotionTap(emotion))
                runCurrent()
                assertEquals(1, posts)
                assertEquals(2, reads)
                assertIs<GroupPressStatus.Reconciling>(viewModel.uiState.value.pressStatus)
                assertFalse(viewModel.onEmotionTap(emotion))
                reconcile.complete(GroupDetailLoadResult.Loaded(detail.copy(todayTotal = 1L)))
                runCurrent()
                assertEquals(2, posts)
                assertEquals(2, reads)
                assertEquals(
                    2L,
                    viewModel.uiState.value.detail
                        ?.todayTotal,
                )
                assertTrue(
                    viewModel.uiState.value.pendingEmotionPresses
                        .isEmpty(),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed reconciliation remains unknown and retry only reads`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var posts = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads++
                            if (reads == 1) GroupDetailLoadResult.Loaded(detail) else GroupDetailLoadResult.Unavailable
                        },
                        press = { _, _ ->
                            posts++
                            PressGroupEmotionResult.OutcomeUnknown
                        },
                    )
                runCurrent()
                val emotion = EmotionKind.entries.first()
                assertTrue(viewModel.onEmotionTap(emotion))
                runCurrent()
                assertIs<GroupPressStatus.OutcomeUnknown>(viewModel.uiState.value.pressStatus)
                assertEquals(1L, viewModel.uiState.value.pendingEmotionPresses[emotion])
                viewModel.onResolvePressOutcome()
                runCurrent()
                assertEquals(3, reads)
                assertEquals(1, posts)
                assertIs<GroupPressStatus.OutcomeUnknown>(viewModel.uiState.value.pressStatus)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `rate limit waits before sending queued press while removing rejected optimism`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var posts = 0
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail) },
                        press = { _, _ ->
                            posts++
                            if (posts == 1) PressGroupEmotionResult.RateLimited(1_000L) else pressed(1)
                        },
                    )
                runCurrent()
                val emotion = EmotionKind.entries.first()
                assertTrue(viewModel.onEmotionTap(emotion))
                assertTrue(viewModel.onEmotionTap(emotion))
                runCurrent()
                assertEquals(1, posts)
                assertEquals(1L, viewModel.uiState.value.pendingEmotionPresses[emotion])
                advanceTimeBy(999L)
                runCurrent()
                assertEquals(1, posts)
                advanceTimeBy(1L)
                runCurrent()
                assertEquals(2, posts)
                assertEquals(
                    1L,
                    viewModel.uiState.value.detail
                        ?.todayTotal,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `membership loss clears active and queued presses`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val response = CompletableDeferred<PressGroupEmotionResult>()
                var posts = 0
                val viewModel =
                    createViewModel(
                        source = { GroupDetailLoadResult.Loaded(detail) },
                        press = { _, _ ->
                            posts++
                            response.await()
                        },
                    )
                runCurrent()
                val emotion = EmotionKind.entries.first()
                assertTrue(viewModel.onEmotionTap(emotion))
                assertTrue(viewModel.onEmotionTap(emotion))
                runCurrent()
                response.complete(PressGroupEmotionResult.MembershipChanged)
                runCurrent()
                assertIs<GroupDetailContent.MembershipChanged>(viewModel.uiState.value.content)
                assertEquals(GroupPressStatus.Idle, viewModel.uiState.value.pressStatus)
                assertTrue(
                    viewModel.uiState.value.pendingEmotionPresses
                        .isEmpty(),
                )
                assertEquals(1, posts)
                assertFalse(viewModel.onEmotionTap(emotion))
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a stale refresh cannot replace a newer confirmed press snapshot`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val staleRefresh = CompletableDeferred<GroupDetailLoadResult>()
                var reads = 0
                val viewModel =
                    createViewModel(
                        source = {
                            reads++
                            if (reads == 1) GroupDetailLoadResult.Loaded(detail) else staleRefresh.await()
                        },
                        press = { _, _ -> pressed(1) },
                    )
                runCurrent()
                viewModel.onRefresh()
                runCurrent()
                assertEquals(2, reads)
                assertTrue(viewModel.onEmotionTap(EmotionKind.entries.first()))
                runCurrent()
                assertEquals(
                    1L,
                    viewModel.uiState.value.detail
                        ?.todayTotal,
                )
                staleRefresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertEquals(
                    1L,
                    viewModel.uiState.value.detail
                        ?.todayTotal,
                )
                assertEquals(GroupPressStatus.Idle, viewModel.uiState.value.pressStatus)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `timed out press is reconciled by GET without sending another POST`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var reads = 0
                var posts = 0
                val neverResponds = CompletableDeferred<PressGroupEmotionResult>()
                val viewModel =
                    GroupDetailViewModel(
                        groupId = detail.group.id,
                        dependencies =
                            GroupDetailDependencies(
                                source = {
                                    reads++
                                    GroupDetailLoadResult.Loaded(detail)
                                },
                                pressGroupEmotionAction = { _, _ ->
                                    posts++
                                    neverResponds.await()
                                },
                                leaveGroupAction = { LeaveGroupResult.Unavailable },
                                errorReporter = { throw it },
                                operationKeyAllocator = GroupOperationKeyAllocator("timeout-test"),
                                requestPolicy = GroupDetailRequestPolicy(timeoutMillis = 100L),
                            ),
                    )
                runCurrent()
                assertTrue(viewModel.onEmotionTap(EmotionKind.entries.first()))
                runCurrent()
                advanceTimeBy(100L)
                runCurrent()
                assertEquals(2, reads)
                assertEquals(1, posts)
                assertEquals(GroupPressStatus.Idle, viewModel.uiState.value.pressStatus)
                assertTrue(
                    viewModel.uiState.value.pendingEmotionPresses
                        .isEmpty(),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun pressed(total: Long): PressGroupEmotionResult.Pressed =
        PressGroupEmotionResult.Pressed(
            GroupPressSnapshotUiModel(
                detail.emotionCounts.mapIndexed { index, count ->
                    count.copy(count = if (index == 0) total else 0L)
                },
                total,
            ),
        )

    @Test
    fun `first resume after initial load does not refresh but later resume does`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val refresh = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel {
                        calls += 1
                        if (calls == 1) GroupDetailLoadResult.Loaded(detail) else refresh.await()
                    }

                runCurrent()
                viewModel.onResumed()
                runCurrent()
                assertEquals(1, calls)
                assertIs<GroupDetailContent.Ready>(viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isRefreshing)

                viewModel.onResumed()
                runCurrent()
                assertEquals(2, calls)
                assertFalse(viewModel.uiState.value.isRefreshing)

                refresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `manual refresh shows indicator while silent resume refresh is in progress`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val refresh = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel {
                        calls += 1
                        if (calls == 1) GroupDetailLoadResult.Loaded(detail) else refresh.await()
                    }

                runCurrent()
                viewModel.onResumed()
                runCurrent()
                viewModel.onResumed()
                runCurrent()
                assertEquals(2, calls)
                assertFalse(viewModel.uiState.value.isRefreshing)

                viewModel.onRefresh()
                runCurrent()
                assertEquals(2, calls)
                assertTrue(viewModel.uiState.value.isRefreshing)

                refresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `first resume during initial load keeps loading and manual refresh still works`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val initial = CompletableDeferred<GroupDetailLoadResult>()
                val refresh = CompletableDeferred<GroupDetailLoadResult>()
                val viewModel =
                    createViewModel {
                        calls += 1
                        if (calls == 1) initial.await() else refresh.await()
                    }

                runCurrent()
                viewModel.onResumed()
                runCurrent()
                assertEquals(1, calls)
                assertEquals(GroupDetailContent.Loading, viewModel.uiState.value.content)
                assertFalse(viewModel.uiState.value.isRefreshing)

                initial.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                viewModel.onRetry()
                runCurrent()
                assertEquals(2, calls)
                assertTrue(viewModel.uiState.value.isRefreshing)

                refresh.complete(GroupDetailLoadResult.Loaded(detail))
                runCurrent()
                assertFalse(viewModel.uiState.value.isRefreshing)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(source: GroupDetailSource): GroupDetailViewModel =
        createViewModel(source, PressGroupEmotionAction { _, _ -> PressGroupEmotionResult.Unavailable })

    private fun createViewModel(
        source: GroupDetailSource,
        press: PressGroupEmotionAction,
    ): GroupDetailViewModel =
        GroupDetailViewModel(
            groupId = detail.group.id,
            dependencies =
                GroupDetailDependencies(
                    source = source,
                    pressGroupEmotionAction = press,
                    leaveGroupAction = { LeaveGroupResult.Unavailable },
                    errorReporter = { throw it },
                    operationKeyAllocator = GroupOperationKeyAllocator("detail-test"),
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 300),
                ),
        )
}
