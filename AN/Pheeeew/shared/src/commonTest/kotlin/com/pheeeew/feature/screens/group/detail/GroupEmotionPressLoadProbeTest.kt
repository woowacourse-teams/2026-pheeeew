package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupEmotionPressLoadProbeTest {
    @Test
    fun `batched requests preserve accepted taps under the same fixed latency`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            val acceptedAt = mutableListOf<Long>()
            val confirmedAt = mutableListOf<Long>()
            var requestCount = 0
            var maxOutstanding = 0
            var activeBatchPressCount = 0
            val confirmedCounts = EmotionKind.entries.associateWith { 0L }.toMutableMap()
            val action =
                PressGroupEmotionAction { _, increments ->
                    requestCount++
                    activeBatchPressCount = increments.sumOf { it.count }
                    delay(RESPONSE_DELAY_MILLIS)
                    increments.forEach { increment ->
                        confirmedCounts[increment.emotion] =
                            confirmedCounts.getValue(increment.emotion) + increment.count
                    }
                    PressGroupEmotionResult.Pressed(
                        GroupPressSnapshotUiModel(
                            emotionCounts =
                                EmotionKind.entries.map { kind ->
                                    EmotionCountUiModel(kind, confirmedCounts.getValue(kind))
                                },
                            total = confirmedCounts.values.sum(),
                        ),
                    )
                }
            val dependencies =
                GroupDetailDependencies(
                    source = { GroupDetailLoadResult.Loaded(detail) },
                    pressGroupEmotionAction = action,
                    leaveGroupAction = { LeaveGroupResult.Unavailable },
                    errorReporter = { throw it },
                    operationKeyAllocator = GroupOperationKeyAllocator("load-probe"),
                    requestPolicy =
                        GroupDetailRequestPolicy(
                            maxOutstandingPresses = MAX_OUTSTANDING_PRESSES,
                            maxPressesPerRequest = MAX_PRESSES_PER_REQUEST,
                            pressBatchWindowMillis = BATCH_WINDOW_MILLIS,
                        ),
                )
            val coordinator =
                GroupEmotionPressCoordinator(
                    groupId = detail.group.id,
                    dependencies = dependencies,
                    scope = this,
                    telemetry = ProductMonitoring(NoOpMonitoring, "group_detail", labels()),
                    onStateChanged = { _, pending, _ ->
                        maxOutstanding = maxOf(maxOutstanding, pending.values.sum().toInt())
                    },
                    onBeforeSend = {},
                    onSnapshot = { _, _ ->
                        repeat(activeBatchPressCount) { confirmedAt += testScheduler.currentTime }
                    },
                    onReconciliationRequested = {},
                    onAccessLost = { _, _ -> },
                    onNotice = { _, _ -> },
                    canContinue = { true },
                )

            repeat(PRESS_COUNT) { index ->
                assertNotNull(coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]))
                acceptedAt += testScheduler.currentTime
                advanceTimeBy(INPUT_INTERVAL_MILLIS)
            }
            advanceUntilIdle()

            val latencies = confirmedAt.zip(acceptedAt).map { (confirmed, accepted) -> confirmed - accepted }.sorted()
            assertEquals(PRESS_COUNT, confirmedAt.size)
            assertTrue(requestCount < PRESS_COUNT)
            assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
            println(
                "PRESS_LOAD_PROBE mode=batch presses=$PRESS_COUNT requests=$requestCount " +
                    "max_outstanding=$maxOutstanding " +
                    "p50_accept_confirm_ms=${latencies[latencies.lastIndex / 2]} " +
                    "p95_accept_confirm_ms=${latencies[(latencies.size * 95 / 100) - 1]}",
            )
        }

    private companion object {
        const val PRESS_COUNT = 100
        const val INPUT_INTERVAL_MILLIS = 5L
        const val RESPONSE_DELAY_MILLIS = 250L
        const val BATCH_WINDOW_MILLIS = 100L
        const val MAX_OUTSTANDING_PRESSES = 300
        const val MAX_PRESSES_PER_REQUEST = 100
    }
}
