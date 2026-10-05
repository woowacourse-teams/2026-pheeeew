package com.pheeeew.feature.screens.group.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.monitoring.product.resultLabel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.withDominantEmotionSummary
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/** Home-entry owner: accepted inputs survive detail-entry removal while this owner is alive. */
class GroupEmotionPressWorkOwner : ViewModel() {
    private val sessions = mutableMapOf<GroupId, GroupEmotionPressSession>()

    internal fun session(
        groupId: GroupId,
        dependencies: GroupDetailDependencies,
    ): GroupEmotionPressSession {
        val current = sessions[groupId]
        if (current != null && !current.accessLost) return current
        val scope =
            CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
        return GroupEmotionPressSession(groupId, dependencies, scope) { finished ->
            // Run after the coordinator finishes draining, including any newly started POST.
            viewModelScope.launch {
                yield()
                if (!finished.hasObservers && !finished.hasTrackedPresses) {
                    if (sessions[groupId] === finished) sessions.remove(groupId)
                    finished.close()
                }
            }
        }.also { sessions[groupId] = it }
    }

    override fun onCleared() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        super.onCleared()
    }

    internal val retainedSessionCount: Int
        get() = sessions.size
}

/** A transmission session holds no screen reference after its observer is detached. Main-thread confined. */
internal class GroupEmotionPressSession(
    private val groupId: GroupId,
    private val dependencies: GroupDetailDependencies,
    private val scope: CoroutineScope,
    private val onPossiblyFinished: (GroupEmotionPressSession) -> Unit,
) {
    interface Observer {
        fun onStateChanged(
            status: GroupPressStatus,
            pending: Map<EmotionKind, Long>,
            unconfirmed: List<GroupUnconfirmedPress>,
            canAcceptAnotherPress: Boolean,
        )

        fun onBeforeSend()

        fun onSnapshot(
            snapshot: GroupPressSnapshotUiModel,
            pending: Map<EmotionKind, Long>,
        )

        fun onReconciledDetail(detail: GroupDetailUiModel)

        fun onAccessLost(
            key: GroupOperationKey,
            reason: GroupDetailAccessLoss,
        )

        fun onNotice(
            kind: GroupDetailNoticeKind,
            retryAfterMillis: Long?,
        )
    }

    private val observers = mutableSetOf<Observer>()
    private val telemetry =
        ProductMonitoring(
            dependencies.monitoring,
            "group_detail",
            labels("group_key" to groupId.value),
        )
    private var reconciliationJob: Job? = null
    private var pendingCounts = emptyMap<EmotionKind, Long>()
    private var unconfirmedPresses = emptyList<GroupUnconfirmedPress>()
    private var canAcceptAnotherPress = true
    var latestDetail: GroupDetailUiModel? = null
        private set
    var accessLost = false
        private set
    private val coordinator =
        GroupEmotionPressCoordinator(
            groupId = groupId,
            dependencies = dependencies,
            scope = scope,
            telemetry = telemetry,
            onStateChanged = { status, pending, unconfirmed, canAccept ->
                pendingCounts = pending
                unconfirmedPresses = unconfirmed
                canAcceptAnotherPress = canAccept
                observers.toList().forEach { it.onStateChanged(status, pending, unconfirmed, canAccept) }
                onPossiblyFinished(this)
            },
            onBeforeSend = { observers.toList().forEach { it.onBeforeSend() } },
            onSnapshot = { snapshot, pending ->
                pendingCounts = pending
                latestDetail = latestDetail?.withPressSnapshot(snapshot)
                observers.toList().forEach { it.onSnapshot(snapshot, pending) }
                onPossiblyFinished(this)
            },
            onReconciliationRequested = ::reconcile,
            onAccessLost = { key, reason ->
                accessLost = true
                latestDetail = null
                observers.toList().forEach { it.onAccessLost(key, reason) }
                onPossiblyFinished(this)
            },
            onNotice = { kind, retryAfter -> observers.toList().forEach { it.onNotice(kind, retryAfter) } },
            canContinue = { !accessLost },
            onWorkFinished = ::publishSettledState,
        )

    val status: GroupPressStatus
        get() = coordinator.status
    val hasPendingWork: Boolean
        get() = coordinator.hasPendingWork || reconciliationJob?.isActive == true
    val hasTrackedPresses: Boolean
        get() = hasPendingWork || coordinator.hasUnconfirmedPresses
    val hasActiveWork: Boolean
        get() = coordinator.hasActiveWork || reconciliationJob?.isActive == true
    val hasObservers: Boolean
        get() = observers.isNotEmpty()

    fun attach(observer: Observer) {
        observers += observer
        observer.onStateChanged(status, pendingCounts, unconfirmedPresses, canAcceptAnotherPress)
    }

    fun detach(observer: Observer) {
        observers -= observer
        onPossiblyFinished(this)
    }

    fun accept(emotion: EmotionKind): GroupOperationKey? = if (accessLost) null else coordinator.accept(emotion)

    fun onDetailLoaded(detail: GroupDetailUiModel): GroupDetailUiModel {
        val processedDetail =
            detail
                .withDominantEmotionSummary(latestDetail?.presentation?.summaryMessage)
                .copy(emotionCounts = detail.emotionCounts.toList())
        latestDetail = processedDetail
        coordinator.onDetailSnapshot(
            GroupPressSnapshotUiModel(
                processedDetail.emotionCounts,
                processedDetail.todayTotal,
            ),
        )
        return processedDetail
    }

    fun resolveUnknown() {
        if (reconciliationJob?.isActive == true) return
        coordinator.resolveUnknown()?.let(::reconcile)
    }

    fun clearForAccessLoss() {
        accessLost = true
        latestDetail = null
        reconciliationJob?.cancel()
        coordinator.clearForAccessLoss()
    }

    fun close() {
        observers.clear()
        scope.cancel()
    }

    private fun publishSettledState() {
        observers.toList().forEach {
            it.onStateChanged(status, pendingCounts, unconfirmedPresses, canAcceptAnotherPress)
        }
        onPossiblyFinished(this)
    }

    private fun reconcile(key: GroupOperationKey) {
        reconciliationJob =
            scope.launch {
                try {
                    val result =
                        try {
                            telemetry.operation("group_detail_load_finished").observe(::resultLabel) {
                                withTimeoutOrNull(dependencies.requestPolicy.timeoutMillis) {
                                    dependencies.source.load(groupId)
                                } ?: GroupDetailLoadResult.Unavailable
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (exception: Exception) {
                            dependencies.errorReporter.reportUnexpected(exception)
                            GroupDetailLoadResult.Unavailable
                        }
                    if (accessLost) return@launch
                    telemetry.emit(
                        "operation_reconciled",
                        labels("operation_kind" to "group_press", "outcome" to resultLabel(result)),
                    )
                    when (result) {
                        is GroupDetailLoadResult.Loaded -> {
                            if (result.detail.group.id == groupId) {
                                val reconciledDetail = onDetailLoaded(result.detail)
                                observers.toList().forEach { it.onReconciledDetail(reconciledDetail) }
                                coordinator.onReconciliationResult(key, succeeded = true)
                            } else {
                                dependencies.errorReporter.reportUnexpected(
                                    IllegalStateException("상세 공급자가 요청한 그룹과 다른 ID를 반환했습니다."),
                                )
                                coordinator.onReconciliationResult(key, succeeded = false)
                            }
                        }

                        GroupDetailLoadResult.MembershipChanged,
                        GroupDetailLoadResult.NotFound,
                        -> {
                            clearForAccessLoss()
                            val reason =
                                if (result == GroupDetailLoadResult.MembershipChanged) {
                                    GroupDetailAccessLoss.MembershipChanged
                                } else {
                                    GroupDetailAccessLoss.NotFound
                                }
                            observers.toList().forEach { it.onAccessLost(key, reason) }
                        }

                        GroupDetailLoadResult.Unavailable -> {
                            coordinator.onReconciliationResult(key, succeeded = false)
                        }
                    }
                } finally {
                    reconciliationJob = null
                    coordinator.drainAfterReconciliation()
                    onPossiblyFinished(this@GroupEmotionPressSession)
                }
            }
    }
}
