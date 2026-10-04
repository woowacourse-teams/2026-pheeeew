package com.pheeeew.feature.screens.group.detail

import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupEmotionPressLoadProbeTest {
    @Test
    fun `single-state request starts immediately and confirms after fake response`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            var requestCount = 0
            var confirmedAt: Long? = null
            val dependencies =
                probeDependencies(detail) { _, _ ->
                    requestCount++
                    delay(RESPONSE_DELAY_MILLIS)
                    PressGroupEmotionResult.Pressed(snapshot(1L))
                }
            val coordinator =
                probeCoordinator(
                    scope = this,
                    detail = detail,
                    dependencies = dependencies,
                    onSnapshot = { _, _ -> confirmedAt = testScheduler.currentTime },
                )
            val acceptedAt = testScheduler.currentTime

            assertNotNull(coordinator.accept(EmotionKind.Angry))
            runCurrent()
            assertEquals(1, requestCount)
            advanceUntilIdle()

            val confirmedLatency = requireNotNull(confirmedAt) - acceptedAt
            assertEquals(RESPONSE_DELAY_MILLIS, confirmedLatency)
            println(
                "PRESS_SINGLE_STATE_PROBE requests=$requestCount accept_confirm_ms=$confirmedLatency",
            )
        }

    @Test
    fun `rapid accepted taps remain FIFO and use one current-contract request each`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            val acceptedAt = mutableListOf<Long>()
            val confirmedAt = mutableListOf<Long>()
            val confirmedCounts = EmotionKind.entries.associateWith { 0L }.toMutableMap()
            var requestCount = 0
            var maxOutstanding = 0
            var pending = emptyMap<EmotionKind, Long>()
            val dependencies =
                probeDependencies(detail) { _, emotion ->
                    requestCount++
                    delay(RESPONSE_DELAY_MILLIS)
                    confirmedCounts[emotion] = confirmedCounts.getValue(emotion) + 1
                    PressGroupEmotionResult.Pressed(snapshot(confirmedCounts.values.sum(), confirmedCounts))
                }
            val coordinator =
                probeCoordinator(
                    scope = this,
                    detail = detail,
                    dependencies = dependencies,
                    onState = { _, counts, _ ->
                        pending = counts
                        maxOutstanding = maxOf(maxOutstanding, counts.values.sum().toInt())
                    },
                    onSnapshot = { _, _ -> confirmedAt += testScheduler.currentTime },
                )

            repeat(PRESS_COUNT) { index ->
                acceptedAt += testScheduler.currentTime
                assertNotNull(coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]))
                advanceTimeBy(INPUT_INTERVAL_MILLIS)
            }
            advanceUntilIdle()

            val latencies = confirmedAt.zip(acceptedAt).map { (confirmed, accepted) -> confirmed - accepted }.sorted()
            assertEquals(PRESS_COUNT, confirmedAt.size)
            assertEquals(PRESS_COUNT, requestCount)
            assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
            assertEquals(emptyMap(), pending)
            println(
                "PRESS_SINGLE_STATE_BURST presses=$PRESS_COUNT requests=$requestCount " +
                    "max_outstanding=$maxOutstanding " +
                    "p50_accept_confirm_ms=${latencies[latencies.lastIndex / 2]} " +
                    "p95_accept_confirm_ms=${latencies[(latencies.size * 95 / 100) - 1]}",
            )
        }

    @Test
    fun `429 drops only the rejected single press and later accepted presses continue after retry after`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            val acceptedAt = ArrayDeque<Long>()
            val confirmedLatencies = mutableListOf<Long>()
            val rejectedLatencies = mutableListOf<Long>()
            var requestCount = 0
            var confirmedCount = 0
            var rejectedCount = 0
            var maxOutstanding = 0
            var pending = emptyMap<EmotionKind, Long>()
            val dependencies =
                probeDependencies(detail) { _, _ ->
                    requestCount++
                    val pressedAt = acceptedAt.removeFirst()
                    delay(RESPONSE_DELAY_MILLIS)
                    if (requestCount <= RATE_LIMITED_REQUESTS) {
                        rejectedCount++
                        rejectedLatencies += testScheduler.currentTime - pressedAt
                        PressGroupEmotionResult.RateLimited(RETRY_AFTER_MILLIS)
                    } else {
                        confirmedCount++
                        confirmedLatencies += testScheduler.currentTime - pressedAt
                        PressGroupEmotionResult.Pressed(snapshot(confirmedCount.toLong()))
                    }
                }
            val coordinator =
                probeCoordinator(
                    scope = this,
                    detail = detail,
                    dependencies = dependencies,
                    onState = { _, counts, _ ->
                        pending = counts
                        maxOutstanding = maxOf(maxOutstanding, counts.values.sum().toInt())
                    },
                )

            repeat(RATE_LIMIT_PRESS_COUNT) { index ->
                acceptedAt.addLast(testScheduler.currentTime)
                assertNotNull(coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]))
                advanceTimeBy(INPUT_INTERVAL_MILLIS)
            }
            advanceUntilIdle()

            assertEquals(RATE_LIMIT_PRESS_COUNT, confirmedCount + rejectedCount)
            assertEquals(RATE_LIMIT_PRESS_COUNT, requestCount)
            assertTrue(rejectedCount > 0)
            assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
            assertEquals(emptyMap(), pending)
            println(
                "PRESS_SINGLE_STATE_429 accepted=$RATE_LIMIT_PRESS_COUNT confirmed=$confirmedCount " +
                    "rate_limited=$rejectedCount requests=$requestCount max_outstanding=$maxOutstanding " +
                    "p50_confirm_ms=${confirmedLatencies.sorted()[confirmedLatencies.size / 2]} " +
                    "p50_rate_limited_ms=${rejectedLatencies.sorted()[rejectedLatencies.size / 2]}",
            )
        }

    @Test
    fun `queue cap rejects excess high-rate taps without losing accepted taps`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            var accepted = 0
            var rejected = 0
            var confirmed = 0
            var requestCount = 0
            var maxOutstanding = 0
            val dependencies =
                probeDependencies(
                    detail,
                    policy = GroupDetailRequestPolicy(maxOutstandingPresses = MAX_OUTSTANDING_PRESSES),
                ) { _, _ ->
                    requestCount++
                    delay(RESPONSE_DELAY_MILLIS)
                    confirmed++
                    PressGroupEmotionResult.Pressed(snapshot(confirmed.toLong()))
                }
            val coordinator =
                probeCoordinator(
                    scope = this,
                    detail = detail,
                    dependencies = dependencies,
                    onState = { _, counts, _ -> maxOutstanding = maxOf(maxOutstanding, counts.values.sum().toInt()) },
                )

            repeat(HIGH_LOAD_PRESS_COUNT) { index ->
                if (coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]) == null) {
                    rejected++
                } else {
                    accepted++
                }
                advanceTimeBy(HIGH_LOAD_INPUT_INTERVAL_MILLIS)
            }
            advanceUntilIdle()

            assertEquals(accepted, confirmed)
            assertEquals(HIGH_LOAD_PRESS_COUNT, accepted + rejected)
            assertEquals(accepted, requestCount)
            assertTrue(rejected > 0, "The high-rate input stream should reach the configured cap")
            assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
            println(
                "PRESS_SINGLE_STATE_SATURATION offered=$HIGH_LOAD_PRESS_COUNT accepted=$accepted " +
                    "confirmed=$confirmed rejected=$rejected requests=$requestCount max_outstanding=$maxOutstanding",
            )
        }

    private fun probeDependencies(
        detail: GroupDetailUiModel,
        policy: GroupDetailRequestPolicy = GroupDetailRequestPolicy(maxOutstandingPresses = MAX_OUTSTANDING_PRESSES),
        press: PressGroupEmotionAction,
    ) = GroupDetailDependencies(
        source = { GroupDetailLoadResult.Loaded(detail) },
        pressGroupEmotionAction = press,
        leaveGroupAction = { LeaveGroupResult.Unavailable },
        errorReporter = { throw it },
        operationKeyAllocator = GroupOperationKeyAllocator("single-state-probe"),
        requestPolicy = policy,
    )

    private fun probeCoordinator(
        scope: CoroutineScope,
        detail: GroupDetailUiModel,
        dependencies: GroupDetailDependencies,
        onState: (GroupPressStatus, Map<EmotionKind, Long>, Boolean) -> Unit = { _, _, _ -> },
        onSnapshot: (GroupPressSnapshotUiModel, Map<EmotionKind, Long>) -> Unit = { _, _ -> },
    ) = GroupEmotionPressCoordinator(
        groupId = detail.group.id,
        dependencies = dependencies,
        scope = scope,
        telemetry = ProductMonitoring(NoOpMonitoring, "group_detail", labels()),
        onStateChanged = onState,
        onBeforeSend = {},
        onSnapshot = onSnapshot,
        onReconciliationRequested = {},
        onAccessLost = { _, _ -> },
        onNotice = { _, _ -> },
        canContinue = { true },
    )

    private fun snapshot(
        total: Long,
        counts: Map<EmotionKind, Long> = EmotionKind.entries.associateWith { 0L },
    ): GroupPressSnapshotUiModel =
        GroupPressSnapshotUiModel(
            emotionCounts = EmotionKind.entries.map { kind -> EmotionCountUiModel(kind, counts.getValue(kind)) },
            total = total,
        )

    private companion object {
        const val PRESS_COUNT = 100
        const val INPUT_INTERVAL_MILLIS = 5L
        const val RESPONSE_DELAY_MILLIS = 250L
        const val MAX_OUTSTANDING_PRESSES = 300
        const val RATE_LIMIT_PRESS_COUNT = 200
        const val RATE_LIMITED_REQUESTS = 2
        const val RETRY_AFTER_MILLIS = 500L
        const val HIGH_LOAD_PRESS_COUNT = 1_000
        const val HIGH_LOAD_INPUT_INTERVAL_MILLIS = 1L
    }
}
