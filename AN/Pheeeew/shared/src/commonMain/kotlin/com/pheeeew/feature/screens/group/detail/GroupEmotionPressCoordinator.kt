package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owns accepted presses and their outcomes. A read is requested for an unknown POST, never a replay. */
internal class GroupEmotionPressCoordinator(
    private val groupId: GroupId,
    private val dependencies: GroupDetailDependencies,
    private val scope: CoroutineScope,
    private val telemetry: ProductMonitoring,
    private val onStateChanged: (GroupPressStatus, Map<EmotionKind, Long>) -> Unit,
    private val onBeforeSend: () -> Unit,
    private val onSnapshot: (GroupPressSnapshotUiModel, Map<EmotionKind, Long>) -> Unit,
    private val onReconciliationRequested: (GroupOperationKey) -> Unit,
    private val onAccessLost: (GroupOperationKey, GroupDetailAccessLoss) -> Unit,
    private val onNotice: (GroupDetailNoticeKind, Long?) -> Unit,
    private val canContinue: () -> Boolean,
) {
    private data class AcceptedPress(
        val emotion: EmotionKind,
        val key: GroupOperationKey,
    )

    private val pending = mutableListOf<AcceptedPress>()
    private val queue = ArrayDeque<AcceptedPress>()
    private var job: Job? = null
    private var generation = 0L
    var status: GroupPressStatus = GroupPressStatus.Idle
        private set
    var confirmedSnapshot: GroupPressSnapshotUiModel? = null
        private set

    val hasPendingWork: Boolean
        get() = status != GroupPressStatus.Idle || job?.isActive == true || pending.isNotEmpty()

    fun accept(emotion: EmotionKind): GroupOperationKey? {
        if (status !is GroupPressStatus.Idle && status !is GroupPressStatus.Sending) return null
        val press = AcceptedPress(emotion, dependencies.operationKeyAllocator.next())
        pending += press
        if (status is GroupPressStatus.Sending || job?.isActive == true) {
            queue.addLast(press)
            publish()
        } else {
            send(press)
        }
        return press.key
    }

    fun resolveUnknown(): GroupOperationKey? {
        val unknown = status as? GroupPressStatus.OutcomeUnknown ?: return null
        status = GroupPressStatus.Reconciling(unknown.operationKey, unknown.emotion)
        publish()
        return unknown.operationKey
    }

    fun onReconciliationResult(
        key: GroupOperationKey,
        succeeded: Boolean,
    ) {
        val reconciling = status as? GroupPressStatus.Reconciling ?: return
        if (reconciling.operationKey != key) return
        if (succeeded) {
            pending.removeAll { it.key == key }
            status = GroupPressStatus.Idle
        } else {
            status = GroupPressStatus.OutcomeUnknown(key, reconciling.emotion)
        }
        publish()
    }

    fun drainAfterReconciliation() = drain()

    fun onDetailSnapshot(snapshot: GroupPressSnapshotUiModel) {
        confirmedSnapshot = snapshot
    }

    fun clearForAccessLoss() {
        generation++
        queue.clear()
        pending.clear()
        status = GroupPressStatus.Idle
        publish()
    }

    private fun send(press: AcceptedPress) {
        if (status != GroupPressStatus.Idle || !canContinue()) {
            clearForAccessLoss()
            return
        }
        val requestGeneration = ++generation
        onBeforeSend()
        status = GroupPressStatus.Sending(press.key, press.emotion)
        publish()
        job =
            scope.launch {
                try {
                    val result =
                        try {
                            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                                telemetry
                                    .operation(
                                        "group_emotion_press_finished",
                                        labels(
                                            "group_operation_key" to
                                                "${press.key.ownerInstanceId}:${press.key.sequence}",
                                        ),
                                        started = "group_emotion_press_started",
                                    ).observe(::resultLabel) {
                                        dependencies.pressGroupEmotionAction.press(groupId, press.emotion)
                                    }
                            } ?: PressGroupEmotionResult.OutcomeUnknown
                        } catch (cancellation: CancellationException) {
                            if (requestGeneration == generation && isSending(press.key)) {
                                status = GroupPressStatus.OutcomeUnknown(press.key, press.emotion)
                                publish()
                            }
                            throw cancellation
                        } catch (exception: Exception) {
                            dependencies.errorReporter.reportUnexpected(exception)
                            PressGroupEmotionResult.OutcomeUnknown
                        }
                    if (requestGeneration != generation || !isSending(press.key)) return@launch
                    handle(press, result)
                    if (result is PressGroupEmotionResult.RateLimited) {
                        delay((result.retryAfterMillis ?: DEFAULT_PRESS_RETRY_DELAY_MILLIS).coerceAtLeast(0L))
                    }
                } finally {
                    if (job === currentCoroutineContext()[Job]) {
                        job = null
                        drain()
                    }
                }
            }
    }

    private fun handle(
        press: AcceptedPress,
        result: PressGroupEmotionResult,
    ) {
        when (result) {
            is PressGroupEmotionResult.Pressed -> {
                confirmedSnapshot = result.snapshot
                pending.removeAll { it.key == press.key }
                status = GroupPressStatus.Idle
                onSnapshot(result.snapshot, pendingCounts())
            }

            PressGroupEmotionResult.MembershipChanged -> {
                clearForAccessLoss()
                onAccessLost(press.key, GroupDetailAccessLoss.MembershipChanged)
            }

            PressGroupEmotionResult.NotFound -> {
                clearForAccessLoss()
                onAccessLost(press.key, GroupDetailAccessLoss.NotFound)
            }

            is PressGroupEmotionResult.RateLimited -> {
                finish(press)
                onNotice(GroupDetailNoticeKind.PressRateLimited, result.retryAfterMillis)
            }

            PressGroupEmotionResult.Rejected -> {
                finish(press)
                onNotice(GroupDetailNoticeKind.PressRejected, null)
            }

            PressGroupEmotionResult.Unavailable -> {
                finish(press)
                onNotice(GroupDetailNoticeKind.PressUnavailable, null)
            }

            PressGroupEmotionResult.OutcomeUnknown -> {
                status = GroupPressStatus.Reconciling(press.key, press.emotion)
                publish()
                onReconciliationRequested(press.key)
            }
        }
    }

    private fun finish(press: AcceptedPress) {
        pending.removeAll { it.key == press.key }
        status = GroupPressStatus.Idle
        publish()
    }

    private fun drain() {
        if (job?.isActive == true || status != GroupPressStatus.Idle) return
        if (!canContinue()) {
            clearForAccessLoss()
            return
        }
        queue.removeFirstOrNull()?.let(::send)
    }

    private fun isSending(key: GroupOperationKey): Boolean = (status as? GroupPressStatus.Sending)?.operationKey == key

    private fun publish() {
        onStateChanged(status, pendingCounts())
    }

    private fun pendingCounts(): Map<EmotionKind, Long> =
        pending.groupingBy { it.emotion }.eachCount().mapValues { it.value.toLong() }
}

private const val DEFAULT_PRESS_RETRY_DELAY_MILLIS = 1_000L
