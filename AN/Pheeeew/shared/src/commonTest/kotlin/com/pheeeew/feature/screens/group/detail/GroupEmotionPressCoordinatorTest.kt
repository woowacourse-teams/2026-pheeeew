package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GroupEmotionPressCoordinatorTest {
    private val detail = fixtureDetail(0L, GroupDetailPresentationKind.FirstStart)
    private val emotion = EmotionKind.entries.first()

    @Test
    fun `rejected and unavailable inputs clear only their own keys and keep FIFO queue`() =
        runTest {
            val responses = List(3) { CompletableDeferred<PressGroupEmotionResult>() }
            var calls = 0
            val notices = mutableListOf<GroupDetailNoticeKind>()
            var pending = emptyMap<EmotionKind, Long>()
            val coordinator =
                coordinator(
                    press = { _, _ -> responses[calls++].await() },
                    onState = { _, counts, _, _ -> pending = counts },
                    onNotice = { kind, _ -> notices += kind },
                )
            val keys = List(3) { assertNotNull(coordinator.accept(emotion)) }
            assertEquals(3, keys.toSet().size)
            assertEquals(3L, pending[emotion])
            runCurrent()
            assertEquals(1, calls)

            responses[0].complete(PressGroupEmotionResult.Rejected)
            runCurrent()
            assertEquals(2, calls)
            assertEquals(2L, pending[emotion])
            responses[1].complete(PressGroupEmotionResult.Unavailable)
            runCurrent()
            assertEquals(3, calls)
            assertEquals(1L, pending[emotion])
            responses[2].complete(PressGroupEmotionResult.Rejected)
            runCurrent()
            assertEquals(emptyMap(), pending)
            assertEquals(GroupPressStatus.Idle, coordinator.status)
            assertEquals(
                listOf(
                    GroupDetailNoticeKind.PressRejected,
                    GroupDetailNoticeKind.PressUnavailable,
                    GroupDetailNoticeKind.PressRejected,
                ),
                notices,
            )
        }

    @Test
    fun `unknown press retains its key until read confirmation and never starts queued POST early`() =
        runTest {
            var calls = 0
            var readKey: GroupOperationKey? = null
            val coordinator =
                coordinator(
                    press = { _, _ ->
                        calls++
                        PressGroupEmotionResult.OutcomeUnknown
                    },
                    onReconcile = { readKey = it },
                )
            val first = assertNotNull(coordinator.accept(emotion))
            coordinator.accept(emotion)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(first, readKey)
            assertIs<GroupPressStatus.Reconciling>(coordinator.status)
            assertNull(coordinator.accept(emotion))

            coordinator.onReconciliationResult(first, succeeded = false)
            assertIs<GroupPressStatus.OutcomeUnknown>(coordinator.status)
            assertEquals(first, coordinator.resolveUnknown())
            coordinator.onReconciliationResult(first, succeeded = true)
            coordinator.drainAfterReconciliation()
            runCurrent()
            assertEquals(2, calls)
        }

    @Test
    fun `unknown input remains separately tracked through reconciliation and is not replayed`() =
        runTest {
            var calls = 0
            var readKey: GroupOperationKey? = null
            var pending = emptyMap<EmotionKind, Long>()
            var unconfirmed = emptyList<GroupUnconfirmedPress>()
            val sent = mutableListOf<EmotionKind>()
            val coordinator =
                coordinator(
                    press = { _, pressedEmotion ->
                        calls++
                        sent += pressedEmotion
                        if (calls == 1) {
                            PressGroupEmotionResult.OutcomeUnknown
                        } else {
                            PressGroupEmotionResult.Pressed(snapshot(calls.toLong()))
                        }
                    },
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 10),
                    onState = { _, counts, inputs, _ ->
                        pending = counts
                        unconfirmed = inputs
                    },
                    onReconcile = { readKey = it },
                )
            val firstKey = assertNotNull(coordinator.accept(emotion))
            assertNotNull(coordinator.accept(EmotionKind.entries[1]))
            assertNotNull(coordinator.accept(emotion))
            runCurrent()

            assertEquals(1, calls)
            assertEquals(firstKey, readKey)
            assertEquals(2L, pending.values.sum())
            assertEquals(listOf(firstKey), unconfirmed.map { it.operationKey })
            assertIs<GroupPressStatus.Reconciling>(coordinator.status)

            coordinator.onReconciliationResult(firstKey, succeeded = true)
            coordinator.drainAfterReconciliation()
            runCurrent()

            assertEquals(listOf(emotion, EmotionKind.entries[1], emotion), sent)
            assertEquals(3, calls)
            assertEquals(emptyMap(), pending)
            assertEquals(listOf(firstKey), unconfirmed.map { it.operationKey })
        }

    @Test
    fun `unconfirmed inputs continue to count toward the outstanding capacity`() =
        runTest {
            var unconfirmed = emptyList<GroupUnconfirmedPress>()
            var canAccept = true
            val coordinator =
                coordinator(
                    press = { _, _ -> PressGroupEmotionResult.OutcomeUnknown },
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 1),
                    onState = { _, _, inputs, capacity ->
                        unconfirmed = inputs
                        canAccept = capacity
                    },
                )

            val key = assertNotNull(coordinator.accept(emotion))
            runCurrent()
            coordinator.onReconciliationResult(key, succeeded = true)

            assertEquals(listOf(key), unconfirmed.map { it.operationKey })
            assertEquals(false, canAccept)
            assertNull(coordinator.accept(EmotionKind.entries[1]))
        }

    @Test
    fun `accepted taps are sent as individual requests in FIFO order`() =
        runTest {
            val requests = mutableListOf<EmotionKind>()
            val coordinator =
                coordinator(
                    press = { _, pressedEmotion ->
                        requests += pressedEmotion
                        PressGroupEmotionResult.Pressed(snapshot(requests.size.toLong()))
                    },
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 300),
                )
            val accepted = List(8) { EmotionKind.entries[it % 2] }
            accepted.forEach { assertNotNull(coordinator.accept(it)) }
            runCurrent()

            assertEquals(accepted, requests)
            assertEquals(GroupPressStatus.Idle, coordinator.status)
        }

    @Test
    fun `single state contract sends one request for each accepted tap`() =
        runTest {
            val requests = mutableListOf<EmotionKind>()
            val coordinator =
                coordinator(
                    press = { _, pressedEmotion ->
                        requests += pressedEmotion
                        PressGroupEmotionResult.Pressed(snapshot(requests.size.toLong()))
                    },
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 5),
                )
            val accepted = List(5) { emotion }
            accepted.forEach { assertNotNull(coordinator.accept(it)) }
            runCurrent()

            assertEquals(accepted, requests)
        }

    @Test
    fun `queue cap blocks only over capacity presses and restores capacity as requests finish`() =
        runTest {
            val firstResponse = CompletableDeferred<PressGroupEmotionResult>()
            val notices = mutableListOf<GroupDetailNoticeKind>()
            var canAccept = true
            val coordinator =
                coordinator(
                    press = { _, _ -> firstResponse.await() },
                    requestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = 3),
                    onState = { _, _, _, capacity -> canAccept = capacity },
                    onNotice = { kind, _ -> notices += kind },
                )
            repeat(3) { assertNotNull(coordinator.accept(emotion)) }
            assertNull(coordinator.accept(emotion))
            assertEquals(false, canAccept)
            assertEquals(listOf(GroupDetailNoticeKind.PressQueueFull), notices)

            runCurrent()
            firstResponse.complete(PressGroupEmotionResult.Pressed(snapshot(1)))
            runCurrent()

            assertEquals(true, canAccept)
            assertNotNull(coordinator.accept(emotion))
        }

    @Test
    fun `rate limits use retry after or fallback and keep cooldown taps queued`() =
        runTest {
            var calls = 0
            var pending = emptyMap<EmotionKind, Long>()
            val coordinator =
                coordinator(
                    press = { _, _ ->
                        when (++calls) {
                            1 -> PressGroupEmotionResult.RateLimited(retryAfterMillis = null)
                            2 -> PressGroupEmotionResult.RateLimited(retryAfterMillis = 500)
                            else -> PressGroupEmotionResult.Pressed(snapshot(calls.toLong()))
                        }
                    },
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = 10,
                            defaultRateLimitDelayMillis = 200,
                        ),
                    onState = { _, counts, _, _ -> pending = counts },
                )
            assertNotNull(coordinator.accept(emotion))
            runCurrent()
            assertIs<GroupPressStatus.CoolingDown>(coordinator.status)
            assertNotNull(coordinator.accept(EmotionKind.entries[1]))
            advanceTimeBy(199)
            runCurrent()
            assertEquals(1, calls)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, calls)
            assertNotNull(coordinator.accept(EmotionKind.entries[2]))
            advanceTimeBy(499)
            runCurrent()
            assertEquals(2, calls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(3, calls)
            advanceUntilIdle()
            assertEquals(GroupPressStatus.Idle, coordinator.status)
            assertEquals(emptyMap(), pending)
        }

    private fun snapshot(total: Long) =
        GroupPressSnapshotUiModel(
            emotionCounts = EmotionKind.entries.map { EmotionCountUiModel(it, 0L) },
            total = total,
        )

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        press: PressGroupEmotionAction,
        requestPolicy: GroupDetailRequestPolicy =
            GroupDetailRequestPolicy(
                maxOutstandingPresses = 300,
            ),
        onState: (GroupPressStatus, Map<EmotionKind, Long>, List<GroupUnconfirmedPress>, Boolean) -> Unit =
            { _, _, _, _ -> },
        onNotice: (GroupDetailNoticeKind, Long?) -> Unit = { _, _ -> },
        onReconcile: (GroupOperationKey) -> Unit = {},
    ): GroupEmotionPressCoordinator {
        val dependencies =
            GroupDetailDependencies(
                source = { GroupDetailLoadResult.Loaded(detail) },
                pressGroupEmotionAction = press,
                leaveGroupAction = { LeaveGroupResult.Unavailable },
                errorReporter = { throw it },
                operationKeyAllocator = GroupOperationKeyAllocator("coordinator-test"),
                requestPolicy = requestPolicy,
            )
        return GroupEmotionPressCoordinator(
            groupId = detail.group.id,
            dependencies = dependencies,
            scope = this,
            telemetry = ProductMonitoring(NoOpMonitoring, "group_detail", labels("group_key" to detail.group.id.value)),
            onStateChanged = onState,
            onBeforeSend = {},
            onSnapshot = { _, _ -> },
            onReconciliationRequested = onReconcile,
            onAccessLost = { _, _ -> },
            onNotice = onNotice,
            canContinue = { true },
        )
    }
}
