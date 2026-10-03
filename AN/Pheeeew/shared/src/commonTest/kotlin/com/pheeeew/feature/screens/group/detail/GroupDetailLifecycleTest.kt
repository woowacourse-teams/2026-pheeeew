package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailLifecycleTest {
    private val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
    private val emotion = EmotionKind.entries.first()
    private val stores = mutableListOf<ViewModelStore>()

    @Test
    fun `old home store key reuses terminal membership state but new entry performs fresh load`() =
        scenario {
            var reads = 0
            val dependencies =
                dependencies(source = {
                    reads++
                    GroupDetailLoadResult.Loaded(detail)
                })
            val owner = owner()
            val (old, oldStore) = screen(dependencies, owner)
            runCurrent()
            old.onMoreClick()
            old.onLeaveMenuClick()
            old.onConfirmLeave()
            runCurrent()
            assertIs<GroupDetailContent.MembershipChanged>(old.uiState.value.content)
            // This lookup reproduces the previous host's home-entry + group-ID ownership.
            assertSame(old, oldStore["detail"])
            old.onResumed()
            old.onResumed()
            runCurrent()
            assertEquals(1, reads)
            oldStore.clear()
            val (rejoined, _) = screen(dependencies, owner)
            runCurrent()
            assertEquals(2, reads)
            assertIs<GroupDetailContent.Ready>(rejoined.uiState.value.content)
            assertEquals(GroupDetailOverlay.None, rejoined.uiState.value.overlay)
            assertTrue(rejoined.onEmotionTap(emotion))
            runCurrent()
        }

    @Test
    fun `detail removal keeps accepted FIFO posts and detaches old screen then releases idle session`() =
        scenario {
            val responses = List(3) { CompletableDeferred<PressGroupEmotionResult>() }
            val sent = mutableListOf<EmotionKind>()
            val owner = owner()
            val dependencies =
                dependencies(press = { _, emotion ->
                    sent += emotion
                    responses[sent.lastIndex].await()
                })
            val (first, store) = screen(dependencies, owner)
            runCurrent()
            val emotions = EmotionKind.entries.take(3)
            emotions.forEach { assertTrue(first.onEmotionTap(it)) }
            runCurrent()
            assertTrue(first.onBackRequested())
            store.clear()
            val removedState = first.uiState.value
            responses.forEachIndexed { index, response ->
                response.complete(pressed(index + 1L))
                runCurrent()
            }
            assertEquals(emotions, sent)
            assertEquals(removedState, first.uiState.value)
            assertEquals(0, owner.retainedSessionCount)
        }

    @Test
    fun `reentry during POST inherits pending and waits for drain before new GET`() =
        scenario {
            var reads = 0
            var posts = 0
            val responses = List(2) { CompletableDeferred<PressGroupEmotionResult>() }
            val owner = owner()
            val dependencies =
                dependencies(
                    source = {
                        reads++
                        GroupDetailLoadResult.Loaded(detail.copy(todayTotal = posts.toLong()))
                    },
                    press = { _, _ -> responses[posts++].await() },
                )
            val (first, store) = screen(dependencies, owner)
            runCurrent()
            repeat(2) { assertTrue(first.onEmotionTap(emotion)) }
            runCurrent()
            store.clear()
            val (second, _) = screen(dependencies, owner)
            runCurrent()
            assertEquals(1, reads)
            assertEquals(2L, second.uiState.value.pendingEmotionPresses[emotion])
            assertIs<GroupDetailContent.Ready>(second.uiState.value.content)
            responses[0].complete(pressed(1))
            runCurrent()
            assertEquals(1, reads)
            assertEquals(1L, second.uiState.value.pendingEmotionPresses[emotion])
            responses[1].complete(pressed(2))
            runCurrent()
            assertEquals(2, reads)
            assertEquals(
                2L,
                second.uiState.value.detail
                    ?.todayTotal,
            )
            assertTrue(
                second.uiState.value.pendingEmotionPresses
                    .isEmpty(),
            )
        }

    @Test
    fun `reentry during pending POST preserves the derived dominant emotion summary`() =
        scenario {
            val detailWithCounts =
                detail.copy(
                    emotionCounts =
                        detail.emotionCounts.map { count ->
                            count.copy(count = if (count.kind == EmotionKind.Blocked) 3L else 0L)
                        },
                )
            val response = CompletableDeferred<PressGroupEmotionResult>()
            val owner = owner()
            val dependencies =
                dependencies(
                    source = { GroupDetailLoadResult.Loaded(detailWithCounts) },
                    press = { _, _ -> response.await() },
                )
            val (first, store) = screen(dependencies, owner)
            runCurrent()
            val firstSummary =
                first.uiState.value.detail
                    ?.presentation
                    ?.summaryMessage
            assertEquals(GroupDetailCopyKey.SummaryBlocked, firstSummary)

            assertTrue(first.onEmotionTap(emotion))
            runCurrent()
            store.clear()
            val (reentered, _) = screen(dependencies, owner)
            runCurrent()

            val reenteredSummary =
                reentered.uiState.value.detail
                    ?.presentation
                    ?.summaryMessage
            assertEquals(GroupDetailCopyKey.SummaryBlocked, reenteredSummary)
            response.complete(pressed(4L))
            runCurrent()
            val settledSummary =
                reentered.uiState.value.detail
                    ?.presentation
                    ?.summaryMessage
            assertEquals(GroupDetailCopyKey.SummaryBlocked, settledSummary)
        }

    @Test
    fun `reconciliation publishes the processed dominant emotion summary`() =
        scenario {
            val initialDetail =
                detail.copy(
                    emotionCounts =
                        detail.emotionCounts.map { count ->
                            count.copy(count = if (count.kind == EmotionKind.Blocked) 3L else 0L)
                        },
                )
            val reconciledDetail =
                detail.copy(
                    emotionCounts =
                        detail.emotionCounts.map { count ->
                            count.copy(count = if (count.kind == EmotionKind.Annoyed) 5L else 0L)
                        },
                )
            val reconciliation = CompletableDeferred<GroupDetailLoadResult>()
            var reads = 0
            val owner = owner()
            val dependencies =
                dependencies(
                    source = {
                        if (++reads == 1) GroupDetailLoadResult.Loaded(initialDetail) else reconciliation.await()
                    },
                    press = { _, _ -> PressGroupEmotionResult.OutcomeUnknown },
                )
            val (viewModel, _) = screen(dependencies, owner)
            runCurrent()
            assertTrue(viewModel.onEmotionTap(emotion))
            runCurrent()
            assertEquals(2, reads)

            reconciliation.complete(GroupDetailLoadResult.Loaded(reconciledDetail))
            runCurrent()

            val summary =
                viewModel.uiState.value.detail
                    ?.presentation
                    ?.summaryMessage
            assertEquals(GroupDetailCopyKey.SummaryAnnoyed, summary)
        }

    @Test
    fun `unknown POST read continues without screen and failed read remains recoverable on reentry`() =
        scenario {
            var reads = 0
            var posts = 0
            val reconciliation = CompletableDeferred<GroupDetailLoadResult>()
            val owner = owner()
            val dependencies =
                dependencies(
                    source = {
                        when (++reads) {
                            1 -> GroupDetailLoadResult.Loaded(detail)
                            2 -> reconciliation.await()
                            else -> GroupDetailLoadResult.Loaded(detail.copy(todayTotal = 1L))
                        }
                    },
                    press = { _, _ ->
                        posts++
                        PressGroupEmotionResult.OutcomeUnknown
                    },
                )
            val (first, store) = screen(dependencies, owner)
            runCurrent()
            first.onEmotionTap(emotion)
            runCurrent()
            assertEquals(2, reads)
            store.clear()
            reconciliation.complete(GroupDetailLoadResult.Unavailable)
            runCurrent()
            assertEquals(1, owner.retainedSessionCount)
            val (second, _) = screen(dependencies, owner)
            runCurrent()
            assertEquals(3, reads)
            assertEquals(1, posts)
            assertEquals(GroupPressStatus.Idle, second.uiState.value.pressStatus)
            assertEquals(
                1L,
                second.uiState.value.detail
                    ?.todayTotal,
            )
            assertTrue(
                second.uiState.value.pendingEmotionPresses
                    .isEmpty(),
            )
        }

    @Test
    fun `group A transmission and group B screen are isolated`() =
        scenario {
            val secondDetail = detail.copy(group = detail.group.copy(id = GroupId("other-group")))
            val response = CompletableDeferred<PressGroupEmotionResult>()
            val owner = owner()
            val dependencies =
                dependencies(
                    source = { id ->
                        GroupDetailLoadResult.Loaded(
                            if (id ==
                                detail.group.id
                            ) {
                                detail
                            } else {
                                secondDetail
                            },
                        )
                    },
                    press = { _, _ -> response.await() },
                )
            val (first, firstStore) = screen(dependencies, owner)
            runCurrent()
            first.onEmotionTap(emotion)
            runCurrent()
            firstStore.clear()
            val (second, _) = screen(dependencies, owner, secondDetail.group.id)
            runCurrent()
            response.complete(pressed(1))
            runCurrent()
            assertEquals(
                secondDetail.group.id,
                second.uiState.value.detail
                    ?.group
                    ?.id,
            )
            assertEquals(
                0L,
                second.uiState.value.detail
                    ?.todayTotal,
            )
            assertTrue(
                second.uiState.value.pendingEmotionPresses
                    .isEmpty(),
            )
        }

    @Test
    fun `clearing detail cancels screen GET and leave while home work owner cancels active POST`() =
        scenario {
            var getCancelled = false
            val owner = owner()
            val (_, loadingStore) =
                screen(
                    dependencies(source = {
                        try {
                            awaitCancellation()
                        } finally {
                            getCancelled = true
                        }
                    }),
                    owner,
                )
            runCurrent()
            loadingStore.clear()
            runCurrent()
            assertTrue(getCancelled)

            var leaveCancelled = false
            val (leaving, leaveStore) =
                screen(
                    dependencies(leave = {
                        try {
                            awaitCancellation()
                        } finally {
                            leaveCancelled = true
                        }
                    }),
                    owner,
                )
            runCurrent()
            leaving.onMoreClick()
            leaving.onLeaveMenuClick()
            leaving.onConfirmLeave()
            runCurrent()
            leaveStore.clear()
            runCurrent()
            assertTrue(leaveCancelled)

            var postCancelled = false
            val (pressing, _) =
                screen(
                    dependencies(press = { _, _ ->
                        try {
                            awaitCancellation()
                        } finally {
                            postCancelled = true
                        }
                    }),
                    owner,
                )
            runCurrent()
            pressing.onEmotionTap(emotion)
            runCurrent()
            assertFalse(postCancelled)
            stores.first().clear()
            runCurrent()
            assertTrue(postCancelled)
            assertEquals(0, owner.retainedSessionCount)
        }

    @Test
    fun `resume refresh does not duplicate active POST and refreshes after completion`() =
        scenario {
            var reads = 0
            var posts = 0
            val response = CompletableDeferred<PressGroupEmotionResult>()
            val owner = owner()
            val (viewModel, _) =
                screen(
                    dependencies(
                        source = {
                            reads++
                            GroupDetailLoadResult.Loaded(detail.copy(todayTotal = posts.toLong()))
                        },
                        press = { _, _ ->
                            posts++
                            response.await()
                        },
                    ),
                    owner,
                )
            runCurrent()
            viewModel.onResumed()
            viewModel.onEmotionTap(emotion)
            runCurrent()
            viewModel.onResumed()
            runCurrent()
            assertEquals(1, posts)
            assertEquals(1, reads)
            response.complete(pressed(1))
            runCurrent()
            viewModel.onResumed()
            runCurrent()
            assertEquals(1, posts)
            assertEquals(2, reads)
        }

    @Test
    fun `immediate main execution loads once and releases detached drained session`() =
        scenario(immediate = true) {
            var reads = 0
            val owner = owner()
            val (viewModel, store) =
                screen(
                    dependencies(source = {
                        reads++
                        GroupDetailLoadResult.Loaded(detail)
                    }),
                    owner,
                )
            runCurrent()
            assertEquals(1, reads)
            assertTrue(viewModel.onEmotionTap(emotion))
            runCurrent()
            store.clear()
            runCurrent()
            assertEquals(0, owner.retainedSessionCount)
        }

    @Test
    fun `unknown POST successfully reconciles and drains remaining queue without a screen`() =
        scenario {
            var reads = 0
            var posts = 0
            val reconciliation = CompletableDeferred<GroupDetailLoadResult>()
            val owner = owner()
            val (viewModel, store) =
                screen(
                    dependencies(
                        source = {
                            if (++reads == 1) GroupDetailLoadResult.Loaded(detail) else reconciliation.await()
                        },
                        press = { _, _ ->
                            if (++posts == 1) PressGroupEmotionResult.OutcomeUnknown else pressed(2)
                        },
                    ),
                    owner,
                )
            runCurrent()
            repeat(2) { assertTrue(viewModel.onEmotionTap(emotion)) }
            runCurrent()
            store.clear()
            reconciliation.complete(GroupDetailLoadResult.Loaded(detail.copy(todayTotal = 1L)))
            runCurrent()
            assertEquals(2, posts)
            assertEquals(2, reads)
            assertEquals(0, owner.retainedSessionCount)
        }

    @Test
    fun `access loss discards queued inputs and rejoined entry receives a new session`() =
        scenario {
            var posts = 0
            var reads = 0
            val response = CompletableDeferred<PressGroupEmotionResult>()
            val owner = owner()
            val dependencies =
                dependencies(
                    source = {
                        reads++
                        GroupDetailLoadResult.Loaded(detail)
                    },
                    press = { _, _ ->
                        posts++
                        if (posts == 1) response.await() else pressed(1)
                    },
                )
            val (first, store) = screen(dependencies, owner)
            runCurrent()
            repeat(2) { assertTrue(first.onEmotionTap(emotion)) }
            runCurrent()
            response.complete(PressGroupEmotionResult.MembershipChanged)
            runCurrent()
            assertIs<GroupDetailContent.MembershipChanged>(first.uiState.value.content)
            assertTrue(
                first.uiState.value.pendingEmotionPresses
                    .isEmpty(),
            )
            assertFalse(first.onEmotionTap(emotion))
            assertEquals(1, posts)
            store.clear()
            val (rejoined, _) = screen(dependencies, owner)
            runCurrent()
            assertEquals(2, reads)
            assertTrue(rejoined.onEmotionTap(emotion))
            runCurrent()
            assertEquals(2, posts)
        }

    @Test
    fun `immediate reentry waits for queued POST and rate limit cooldown before fresh read`() =
        scenario(immediate = true) {
            var reads = 0
            var posts = 0
            val response = CompletableDeferred<PressGroupEmotionResult>()
            val owner = owner()
            val dependencies =
                dependencies(
                    source = {
                        reads++
                        GroupDetailLoadResult.Loaded(detail)
                    },
                    press = { _, _ ->
                        posts++
                        if (posts == 1) response.await() else PressGroupEmotionResult.RateLimited(100L)
                    },
                )
            val (first, store) = screen(dependencies, owner)
            repeat(2) { assertTrue(first.onEmotionTap(emotion)) }
            store.clear()
            val (second, _) = screen(dependencies, owner)
            response.complete(pressed(1))
            runCurrent()
            assertEquals(2, posts)
            assertEquals(1, reads)
            assertEquals(GroupPressStatus.Idle, second.uiState.value.pressStatus)
            advanceTimeBy(100L)
            runCurrent()
            assertEquals(2, reads)
            assertTrue(
                second.uiState.value.pendingEmotionPresses
                    .isEmpty(),
            )
        }

    private fun dependencies(
        source: GroupDetailSource = { GroupDetailLoadResult.Loaded(detail) },
        press: PressGroupEmotionAction = { _, _ -> pressed(1) },
        leave: LeaveGroupAction = { LeaveGroupResult.Left },
    ) = GroupDetailDependencies(
        source = source,
        pressGroupEmotionAction = press,
        leaveGroupAction = leave,
        errorReporter = { throw it },
        operationKeyAllocator = GroupOperationKeyAllocator("lifecycle-test"),
    )

    private fun owner(): GroupEmotionPressWorkOwner = GroupEmotionPressWorkOwner().also { retain(it) }

    private fun screen(
        dependencies: GroupDetailDependencies,
        owner: GroupEmotionPressWorkOwner,
        groupId: GroupId = detail.group.id,
    ): Pair<GroupDetailViewModel, ViewModelStore> {
        val viewModel = GroupDetailViewModel(groupId, dependencies, pressWorkOwner = owner)
        return viewModel to retain(viewModel)
    }

    private fun retain(viewModel: ViewModel): ViewModelStore =
        ViewModelStore().also {
            it.put("detail", viewModel)
            stores += it
        }

    private fun pressed(total: Long) =
        PressGroupEmotionResult.Pressed(
            GroupPressSnapshotUiModel(
                detail.emotionCounts.mapIndexed { index, count -> count.copy(count = if (index == 0) total else 0L) },
                total,
            ),
        )

    private fun scenario(
        immediate: Boolean = false,
        block: suspend TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(
            if (immediate) UnconfinedTestDispatcher(testScheduler) else StandardTestDispatcher(testScheduler),
        )
        try {
            block()
        } finally {
            stores.asReversed().forEach(ViewModelStore::clear)
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
