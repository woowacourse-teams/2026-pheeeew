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
    fun `single tap latency is measured across batch window settings`() =
        runTest {
            val windows = listOf(0L, 25L, 50L, 100L, 150L)
            windows.forEach { window ->
                val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
                var confirmedAt: Long? = null
                val dependencies =
                    probeDependencies(
                        detail = detail,
                        policy =
                            GroupDetailRequestPolicy(
                                maxOutstandingPresses = MAX_OUTSTANDING_PRESSES,
                                maxPressesPerRequest = MAX_PRESSES_PER_REQUEST,
                                pressBatchWindowMillis = window,
                            ),
                        press = { _, increments ->
                            delay(RESPONSE_DELAY_MILLIS)
                            assertEquals(1, increments.sumOf { it.count })
                            PressGroupEmotionResult.Pressed(snapshot(1L))
                        },
                    )
                val coordinator =
                    probeCoordinator(
                        scope = this,
                        detail = detail,
                        dependencies = dependencies,
                        onSnapshot = { _, _ -> confirmedAt = testScheduler.currentTime },
                    )
                val acceptedAt = testScheduler.currentTime
                assertNotNull(coordinator.accept(EmotionKind.entries.first()))
                runCurrent()
                advanceUntilIdle()

                val latency = requireNotNull(confirmedAt) - acceptedAt
                assertEquals(window + RESPONSE_DELAY_MILLIS, latency)
                println("PRESS_SINGLE_PROBE window_ms=$window accept_confirm_ms=$latency")
            }
        }

    @Test
    fun `burst efficiency is compared across batch window settings`() =
        runTest {
            listOf(0L, 25L, 50L, 100L, 150L).forEach { window ->
                val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
                val acceptedAt = mutableListOf<Long>()
                val confirmedAt = mutableListOf<Long>()
                var requestCount = 0
                var activeBatchPressCount = 0
                var maxOutstanding = 0
                val counts = EmotionKind.entries.associateWith { 0L }.toMutableMap()
                val dependencies =
                    probeDependencies(
                        detail = detail,
                        policy =
                            GroupDetailRequestPolicy(
                                maxOutstandingPresses = MAX_OUTSTANDING_PRESSES,
                                maxPressesPerRequest = MAX_PRESSES_PER_REQUEST,
                                pressBatchWindowMillis = window,
                            ),
                        press = { _, increments ->
                            requestCount++
                            activeBatchPressCount = increments.sumOf { it.count }
                            delay(RESPONSE_DELAY_MILLIS)
                            increments.forEach { increment ->
                                counts[increment.emotion] = counts.getValue(increment.emotion) + increment.count
                            }
                            PressGroupEmotionResult.Pressed(
                                GroupPressSnapshotUiModel(
                                    emotionCounts =
                                        EmotionKind.entries.map { kind ->
                                            EmotionCountUiModel(kind, counts.getValue(kind))
                                        },
                                    total = counts.values.sum(),
                                ),
                            )
                        },
                    )
                val coordinator =
                    probeCoordinator(
                        scope = this,
                        detail = detail,
                        dependencies = dependencies,
                        onState = { _, pending, _ ->
                            maxOutstanding = maxOf(maxOutstanding, pending.values.sum().toInt())
                        },
                        onSnapshot = { _, _ ->
                            repeat(activeBatchPressCount) { confirmedAt += testScheduler.currentTime }
                        },
                    )

                repeat(PRESS_COUNT) { index ->
                    assertNotNull(coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]))
                    acceptedAt += testScheduler.currentTime
                    advanceTimeBy(INPUT_INTERVAL_MILLIS)
                }
                advanceUntilIdle()

                val latencies =
                    confirmedAt
                        .zip(
                            acceptedAt,
                        ).map { (confirmed, accepted) -> confirmed - accepted }
                        .sorted()
                assertEquals(PRESS_COUNT, confirmedAt.size)
                assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
                println(
                    "PRESS_WINDOW_PROBE window_ms=$window presses=$PRESS_COUNT requests=$requestCount " +
                        "max_outstanding=$maxOutstanding " +
                        "p50_accept_confirm_ms=${latencies[latencies.lastIndex / 2]} " +
                        "p95_accept_confirm_ms=${latencies[(latencies.size * 95 / 100) - 1]}",
                )
            }
        }

    @Test
    fun `repeated rate limits measure terminal latency and retain later accepted taps`() =
        runTest {
            val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
            val acceptedAt = ArrayDeque<Long>()
            val confirmedLatencies = mutableListOf<Long>()
            val rejectedLatencies = mutableListOf<Long>()
            var requestCount = 0
            var confirmedCount = 0
            var rejectedCount = 0
            var maxOutstanding = 0
            var confirmedTotal = 0L
            var pending = emptyMap<EmotionKind, Long>()
            val dependencies =
                probeDependencies(
                    detail = detail,
                    press = { _, increments ->
                        requestCount++
                        val batchCount = increments.sumOf { it.count }
                        val batchAcceptedAt = List(batchCount) { acceptedAt.removeFirst() }
                        delay(RESPONSE_DELAY_MILLIS)
                        if (requestCount <= RATE_LIMITED_REQUESTS) {
                            rejectedCount += batchCount
                            rejectedLatencies += batchAcceptedAt.map { testScheduler.currentTime - it }
                            PressGroupEmotionResult.RateLimited(RETRY_AFTER_MILLIS)
                        } else {
                            confirmedCount += batchCount
                            confirmedTotal += batchCount
                            confirmedLatencies += batchAcceptedAt.map { testScheduler.currentTime - it }
                            PressGroupEmotionResult.Pressed(snapshot(confirmedTotal))
                        }
                    },
                )
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

            repeat(REPEATED_RATE_LIMIT_PRESS_COUNT) { index ->
                val timestamp = testScheduler.currentTime
                assertNotNull(coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]))
                acceptedAt.addLast(timestamp)
                advanceTimeBy(INPUT_INTERVAL_MILLIS)
            }
            advanceUntilIdle()

            assertEquals(REPEATED_RATE_LIMIT_PRESS_COUNT, confirmedCount + rejectedCount)
            assertEquals(3, requestCount)
            assertTrue(rejectedCount > 0)
            assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
            assertEquals(emptyMap(), pending)
            val p50 = confirmedLatencies.sorted()[confirmedLatencies.size / 2]
            val p95 = confirmedLatencies.sorted()[(confirmedLatencies.size * 95 / 100) - 1]
            println(
                "PRESS_429_PROBE accepted=$REPEATED_RATE_LIMIT_PRESS_COUNT confirmed=$confirmedCount " +
                    "rejected=$rejectedCount requests=$requestCount max_outstanding=$maxOutstanding " +
                    "p50_accept_confirm_ms=$p50 p95_accept_confirm_ms=$p95 " +
                    "p50_accept_reject_ms=${rejectedLatencies.sorted()[rejectedLatencies.size / 2]}",
            )
        }

    @Test
    fun `queue saturation measures throughput and explicit rejection at the configured cap`() =
        runTest {
            listOf(50L, 100L).forEach { window ->
                val detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart)
                val acceptedAt = ArrayDeque<Long>()
                val confirmationLatencies = mutableListOf<Long>()
                var accepted = 0
                var rejected = 0
                var confirmed = 0
                var requestCount = 0
                var maxOutstanding = 0
                val dependencies =
                    probeDependencies(
                        detail = detail,
                        policy =
                            GroupDetailRequestPolicy(
                                maxOutstandingPresses = MAX_OUTSTANDING_PRESSES,
                                maxPressesPerRequest = MAX_PRESSES_PER_REQUEST,
                                pressBatchWindowMillis = window,
                            ),
                        press = { _, increments ->
                            requestCount++
                            val batchCount = increments.sumOf { it.count }
                            val batchAcceptedAt = List(batchCount) { acceptedAt.removeFirst() }
                            delay(RESPONSE_DELAY_MILLIS)
                            confirmed += batchCount
                            confirmationLatencies += batchAcceptedAt.map { testScheduler.currentTime - it }
                            PressGroupEmotionResult.Pressed(snapshot(confirmed.toLong()))
                        },
                    )
                val coordinator =
                    probeCoordinator(
                        scope = this,
                        detail = detail,
                        dependencies = dependencies,
                        onState = { _, pending, _ ->
                            maxOutstanding = maxOf(maxOutstanding, pending.values.sum().toInt())
                        },
                    )

                repeat(HIGH_LOAD_PRESS_COUNT) { index ->
                    val timestamp = testScheduler.currentTime
                    if (coordinator.accept(EmotionKind.entries[index % EmotionKind.entries.size]) == null) {
                        rejected++
                    } else {
                        accepted++
                        acceptedAt.addLast(timestamp)
                    }
                    advanceTimeBy(HIGH_LOAD_INPUT_INTERVAL_MILLIS)
                }
                advanceUntilIdle()

                assertEquals(accepted, confirmed)
                assertEquals(HIGH_LOAD_PRESS_COUNT, accepted + rejected)
                assertTrue(rejected > 0, "The high-rate input stream should reach the configured cap")
                assertTrue(maxOutstanding <= MAX_OUTSTANDING_PRESSES)
                val sorted = confirmationLatencies.sorted()
                println(
                    "PRESS_SATURATION_PROBE window_ms=$window offered=$HIGH_LOAD_PRESS_COUNT " +
                        "accepted=$accepted confirmed=$confirmed rejected=$rejected requests=$requestCount " +
                        "max_outstanding=$maxOutstanding p50_accept_confirm_ms=${sorted[sorted.size / 2]} " +
                        "p95_accept_confirm_ms=${sorted[(sorted.size * 95 / 100) - 1]}",
                )
            }
        }

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
        const val REPEATED_RATE_LIMIT_PRESS_COUNT = 200
        const val RATE_LIMITED_REQUESTS = 2
        const val RETRY_AFTER_MILLIS = 500L
        const val HIGH_LOAD_PRESS_COUNT = 1_000
        const val HIGH_LOAD_INPUT_INTERVAL_MILLIS = 1L
    }

    private fun probeDependencies(
        detail: GroupDetailUiModel,
        press: PressGroupEmotionAction,
        policy: GroupDetailRequestPolicy =
            GroupDetailRequestPolicy(
                maxOutstandingPresses = MAX_OUTSTANDING_PRESSES,
                maxPressesPerRequest = MAX_PRESSES_PER_REQUEST,
                pressBatchWindowMillis = BATCH_WINDOW_MILLIS,
            ),
    ) = GroupDetailDependencies(
        source = { GroupDetailLoadResult.Loaded(detail) },
        pressGroupEmotionAction = press,
        leaveGroupAction = { LeaveGroupResult.Unavailable },
        errorReporter = { throw it },
        operationKeyAllocator = GroupOperationKeyAllocator("load-probe"),
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

    private fun snapshot(total: Long) =
        GroupPressSnapshotUiModel(
            emotionCounts =
                EmotionKind.entries.mapIndexed { index, kind ->
                    EmotionCountUiModel(kind, if (index == 0) total else 0L)
                },
            total = total,
        )
}
