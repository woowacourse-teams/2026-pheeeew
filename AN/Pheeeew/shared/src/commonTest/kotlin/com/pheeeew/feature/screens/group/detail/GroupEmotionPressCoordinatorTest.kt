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
                    onState = { _, counts, _ -> pending = counts },
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
    fun `unknown batch remains pending through read reconciliation and is not replayed`() =
        runTest {
            var calls = 0
            var readKey: GroupOperationKey? = null
            var pending = emptyMap<EmotionKind, Long>()
            val sent = mutableListOf<Int>()
            val coordinator =
                coordinator(
                    press = { _, increments ->
                        calls++
                        sent += increments.sumOf { it.count }
                        if (calls == 1) {
                            PressGroupEmotionResult.OutcomeUnknown
                        } else {
                            PressGroupEmotionResult.Pressed(snapshot(calls.toLong()))
                        }
                    },
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = 10,
                            maxPressesPerRequest = 2,
                            pressBatchWindowMillis = 0,
                        ),
                    onState = { _, counts, _ -> pending = counts },
                    onReconcile = { readKey = it },
                )
            val firstKey = assertNotNull(coordinator.accept(emotion))
            assertNotNull(coordinator.accept(EmotionKind.entries[1]))
            assertNotNull(coordinator.accept(emotion))
            runCurrent()

            assertEquals(1, calls)
            assertEquals(firstKey, readKey)
            assertEquals(3L, pending.values.sum())
            assertIs<GroupPressStatus.Reconciling>(coordinator.status)

            coordinator.onReconciliationResult(firstKey, succeeded = true)
            coordinator.drainAfterReconciliation()
            runCurrent()

            assertEquals(listOf(2, 1), sent)
            assertEquals(2, calls)
            assertEquals(emptyMap(), pending)
        }

    @Test
    fun `accepted taps are batched without losing per emotion counts`() =
        runTest {
            val requests = mutableListOf<List<EmotionPressIncrement>>()
            val coordinator =
                coordinator(
                    press = { _, increments ->
                        requests += increments
                        PressGroupEmotionResult.Pressed(snapshot(requests.size.toLong()))
                    },
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = 300,
                            maxPressesPerRequest = 100,
                            pressBatchWindowMillis = 100,
                        ),
                )
            repeat(8) { index ->
                assertNotNull(coordinator.accept(EmotionKind.entries[index % 2]))
            }

            advanceTimeBy(100)
            runCurrent()

            assertEquals(1, requests.size)
            assertEquals(8, requests.single().sumOf { it.count })
            assertEquals(
                mapOf(EmotionKind.entries[0] to 4, EmotionKind.entries[1] to 4),
                requests.single().associate { it.emotion to it.count },
            )
            assertEquals(GroupPressStatus.Idle, coordinator.status)
        }

    @Test
    fun `request batches obey their configured count limit`() =
        runTest {
            val requests = mutableListOf<Int>()
            val coordinator =
                coordinator(
                    press = { _, increments ->
                        requests += increments.sumOf { it.count }
                        PressGroupEmotionResult.Pressed(snapshot(requests.size.toLong()))
                    },
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = 5,
                            maxPressesPerRequest = 2,
                            pressBatchWindowMillis = 0,
                        ),
                )
            repeat(5) { assertNotNull(coordinator.accept(emotion)) }
            advanceUntilIdle()

            assertEquals(listOf(2, 2, 1), requests)
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
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = 3,
                            maxPressesPerRequest = 1,
                            pressBatchWindowMillis = 0,
                        ),
                    onState = { _, _, capacity -> canAccept = capacity },
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
                            maxPressesPerRequest = 1,
                            pressBatchWindowMillis = 0,
                            defaultRateLimitDelayMillis = 200,
                        ),
                    onState = { _, counts, _ -> pending = counts },
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
                maxPressesPerRequest = 1,
                pressBatchWindowMillis = 0,
            ),
        onState: (GroupPressStatus, Map<EmotionKind, Long>, Boolean) -> Unit = { _, _, _ -> },
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
