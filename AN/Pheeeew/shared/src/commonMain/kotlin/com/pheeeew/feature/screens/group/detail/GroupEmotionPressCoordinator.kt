package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val onStateChanged: (GroupPressStatus, Map<EmotionKind, Long>, Boolean) -> Unit,
    private val onBeforeSend: () -> Unit,
    private val onSnapshot: (GroupPressSnapshotUiModel, Map<EmotionKind, Long>) -> Unit,
    private val onReconciliationRequested: (GroupOperationKey) -> Unit,
    private val onAccessLost: (GroupOperationKey, GroupDetailAccessLoss) -> Unit,
    private val onNotice: (GroupDetailNoticeKind, Long?) -> Unit,
    private val canContinue: () -> Boolean,
    private val onWorkFinished: () -> Unit = {},
) {
    private data class AcceptedPress(
        val emotion: EmotionKind,
        val key: GroupOperationKey,
    )

    private val pending = mutableListOf<AcceptedPress>()
    private val queue = ArrayDeque<AcceptedPress>()
    private var job: Job? = null
    private var generation = 0L
    private var unknownBatch: List<AcceptedPress> = emptyList()

    var status: GroupPressStatus = GroupPressStatus.Idle
        private set
    var confirmedSnapshot: GroupPressSnapshotUiModel? = null
        private set

    val hasPendingWork: Boolean
        get() = status != GroupPressStatus.Idle || job?.isActive == true || pending.isNotEmpty()

    fun accept(emotion: EmotionKind): GroupOperationKey? {
        if (status !is GroupPressStatus.Idle && status !is GroupPressStatus.Sending &&
            status !is GroupPressStatus.CoolingDown
        ) {
            return null
        }
        if (pending.size >= dependencies.requestPolicy.maxOutstandingPresses) return null

        val press = AcceptedPress(emotion, dependencies.operationKeyAllocator.next())
        pending += press
        queue.addLast(press)
        publish()

        if (pending.size == dependencies.requestPolicy.maxOutstandingPresses) {
            onNotice(GroupDetailNoticeKind.PressQueueFull, null)
        }
        if (status == GroupPressStatus.Idle && job?.isActive != true) {
            startProcessing()
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
            val reconciledKeys = unknownBatch.mapTo(mutableSetOf()) { it.key }.ifEmpty { setOf(key) }
            pending.removeAll { it.key in reconciledKeys }
            unknownBatch = emptyList()
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
        val activeJob = job
        job = null
        activeJob?.cancel()
        queue.clear()
        pending.clear()
        unknownBatch = emptyList()
        status = GroupPressStatus.Idle
        publish()
    }

    private fun startProcessing() {
        if (job?.isActive == true || status != GroupPressStatus.Idle) return
        if (!canContinue()) {
            clearForAccessLoss()
            return
        }
        val firstPress = queue.firstOrNull() ?: return
        val processingGeneration = ++generation
        status = GroupPressStatus.Sending(firstPress.key, firstPress.emotion)
        publish()

        val processingJob =
            scope.launch(start = CoroutineStart.LAZY) {
                var activeBatch: List<AcceptedPress> = emptyList()
                try {
                    while (processingGeneration == generation && canContinue()) {
                        val batch = takeBatch()
                        if (batch.isEmpty()) {
                            status = GroupPressStatus.Idle
                            publish()
                            return@launch
                        }
                        activeBatch = batch
                        val first = batch.first()
                        onBeforeSend()
                        status = GroupPressStatus.Sending(first.key, first.emotion)
                        publish()
                        val result = request(batch)
                        if (processingGeneration != generation || !isSending(first.key)) return@launch

                        handle(batch, result)
                        if (result is PressGroupEmotionResult.RateLimited && status is GroupPressStatus.CoolingDown) {
                            val cooldownMillis = (status as GroupPressStatus.CoolingDown).retryAfterMillis
                            delay(cooldownMillis)
                            if (processingGeneration != generation) return@launch
                            status = GroupPressStatus.Idle
                            publish()
                        }
                        activeBatch = emptyList()
                        if (status != GroupPressStatus.Idle || queue.isEmpty()) return@launch
                    }
                    if (processingGeneration == generation && !canContinue()) clearForAccessLoss()
                } catch (cancelled: CancellationException) {
                    if (processingGeneration == generation && activeBatch.isNotEmpty() &&
                        isSending(activeBatch.first().key)
                    ) {
                        val first = activeBatch.first()
                        unknownBatch = activeBatch
                        status = GroupPressStatus.OutcomeUnknown(first.key, first.emotion)
                        publish()
                    }
                    throw cancelled
                } finally {
                    if (job === currentCoroutineContext()[Job]) {
                        job = null
                        drain()
                    }
                    onWorkFinished()
                }
            }
        job = processingJob
        processingJob.start()
    }

    private suspend fun request(batch: List<AcceptedPress>): PressGroupEmotionResult {
        val first = batch.first()
        return try {
            withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                telemetry
                    .operation(
                        "group_emotion_press_finished",
                        labels(
                            "group_operation_key" to "${first.key.ownerInstanceId}:${first.key.sequence}",
                            "press_count" to "1",
                            "emotion_count" to "1",
                        ),
                        started = "group_emotion_press_started",
                    ).observe(::resultLabel) {
                        dependencies.pressGroupEmotionAction.press(groupId, first.emotion)
                    }
            } ?: PressGroupEmotionResult.OutcomeUnknown
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            dependencies.errorReporter.reportUnexpected(exception)
            PressGroupEmotionResult.OutcomeUnknown
        }
    }

    private fun handle(
        batch: List<AcceptedPress>,
        result: PressGroupEmotionResult,
    ) {
        val first = batch.first()
        when (result) {
            is PressGroupEmotionResult.Pressed -> {
                confirmedSnapshot = result.snapshot
                removePending(batch)
                status = GroupPressStatus.Idle
                onSnapshot(result.snapshot, pendingCounts())
                publish()
            }

            PressGroupEmotionResult.MembershipChanged -> {
                clearForAccessLoss()
                onAccessLost(first.key, GroupDetailAccessLoss.MembershipChanged)
            }

            PressGroupEmotionResult.NotFound -> {
                clearForAccessLoss()
                onAccessLost(first.key, GroupDetailAccessLoss.NotFound)
            }

            is PressGroupEmotionResult.RateLimited -> {
                removePending(batch)
                val requestedCooldownMillis =
                    result.retryAfterMillis ?: dependencies.requestPolicy.defaultRateLimitDelayMillis
                val cooldownMillis = requestedCooldownMillis.coerceAtLeast(0L)
                status = GroupPressStatus.CoolingDown(cooldownMillis)
                publish()
                onNotice(GroupDetailNoticeKind.PressRateLimited, cooldownMillis)
            }

            PressGroupEmotionResult.Rejected -> {
                finish(batch)
                onNotice(GroupDetailNoticeKind.PressRejected, null)
            }

            PressGroupEmotionResult.Unavailable -> {
                finish(batch)
                onNotice(GroupDetailNoticeKind.PressUnavailable, null)
            }

            PressGroupEmotionResult.OutcomeUnknown -> {
                unknownBatch = batch
                status = GroupPressStatus.Reconciling(first.key, first.emotion)
                publish()
                onReconciliationRequested(first.key)
            }
        }
    }

    private fun finish(batch: List<AcceptedPress>) {
        removePending(batch)
        status = GroupPressStatus.Idle
        publish()
    }

    private fun removePending(batch: List<AcceptedPress>) {
        val keys = batch.mapTo(mutableSetOf()) { it.key }
        pending.removeAll { it.key in keys }
    }

    private fun takeBatch(): List<AcceptedPress> = queue.removeFirstOrNull()?.let(::listOf).orEmpty()

    private fun drain() {
        if (job?.isActive == true || status != GroupPressStatus.Idle) return
        if (!canContinue()) {
            clearForAccessLoss()
            return
        }
        if (queue.isNotEmpty()) startProcessing()
    }

    private fun isSending(key: GroupOperationKey): Boolean = (status as? GroupPressStatus.Sending)?.operationKey == key

    private fun publish() {
        onStateChanged(
            status,
            pendingCounts(),
            pending.size < dependencies.requestPolicy.maxOutstandingPresses,
        )
    }

    private fun pendingCounts(): Map<EmotionKind, Long> =
        pending.groupingBy { it.emotion }.eachCount().mapValues { it.value.toLong() }
}
