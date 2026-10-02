package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                    onState = { _, counts -> pending = counts },
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

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        press: PressGroupEmotionAction,
        onState: (GroupPressStatus, Map<EmotionKind, Long>) -> Unit = { _, _ -> },
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
