package com.pheeeew.data.press.session

import com.pheeeew.core.monitoring.ActivityType
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.data.press.delivery.PressWriteState
import com.pheeeew.data.press.read.PressReadContext
import com.pheeeew.data.press.read.PressReadCoordinator
import com.pheeeew.data.press.read.PressReadEvent
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.press.batching.PressFlushPolicy
import com.pheeeew.domain.press.batching.PressInputQueue
import com.pheeeew.domain.press.batching.PressQueueAcceptance
import com.pheeeew.domain.press.settlement.PressPendingWrite
import com.pheeeew.domain.press.settlement.PressWriteResolution
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressEpochMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressSender
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 앱 세션 동안 개인 프레스의 입력, 전송, 조회를 조율한다.
 *
 * 입력 큐와 전송 원장 변경은 모두 상태를 소유한 실행 흐름에서 순서대로 처리한다. 이 클래스는 각 규칙의
 * 세부 계산을 직접 하기보다 큐·쓰기 세션·조회 상태·API 어댑터를 올바른 순서로 연결하고,
 * 그 결과를 [state]로 공개한다.
 */
internal class PressSession(
    statistics: PressStatisticsDataSource,
    private val sender: PressSender,
    private val sessionScope: CoroutineScope,
    batchMaxPerEmotion: Int,
    batchMaxTotal: Int,
    flushDelayMillis: PressDelayMillis,
    private val retryPolicy: PressRetryPolicy,
    aggregateRefreshMillis: PressDelayMillis,
    connectivity: Flow<Boolean>?,
    private val nowMillis: () -> PressMonotonicMillis,
    private val monitoring: Monitoring,
    private val wallClock: () -> PressEpochMillis,
) {
    // 공개 상태와 입력·쓰기·조회 협력 객체를 보관한다.
    private val mutableState = MutableStateFlow(PressSessionState())
    val state = mutableState.asStateFlow()
    private val inputQueue = PressInputQueue(batchMaxPerEmotion, batchMaxTotal, flushDelayMillis)
    private val writeSession = PressWriteSession()
    private val reads =
        PressReadCoordinator(
            statistics = statistics,
            scope = sessionScope,
            retryPolicy = retryPolicy,
            aggregateRefreshMillis = aggregateRefreshMillis,
            nowMillis = nowMillis,
            onEvent = ::handleReadEvent,
        )
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)

    // 전송·재시도 작업과 세션 진행 상태를 보관한다.
    private var workerJob: Job? = null
    private var retryJob: Job? = null

    private var hasStarted = false
    private var pausedNotice: PressSessionNotice? = null
    private var connected: Boolean? = null
    private var sendFailureCount = 0
    private var sendRetryDeadline = PressMonotonicMillis(0L)

    init {
        connectivity?.let { updates ->
            sessionScope.launch {
                updates.distinctUntilChanged().collect { available ->
                    connected = available
                    reads.onConnectivityChanged(available)
                    if (available) {
                        onForeground()
                    } else if (writeSession.hasOutstanding() && pausedNotice == null) {
                        publish(mutableState.value.copy(notice = PressSessionNotice.RetryRequired))
                    }
                }
            }
        }
    }

    /** 입력 한 건을 한도 검사 후 큐와 낙관적 상태에 반영하고 묶음 전송을 예약한다. */
    fun accept(emotion: EmotionState): PressAcceptance {
        val serverTotal = mutableState.value.myToday?.total ?: 0L
        val allServerTotal = mutableState.value.allToday?.total ?: 0L
        if (writeSession.exceedsAcceptanceLimit(serverTotal, allServerTotal)) {
            mutableState.update { it.copy(notice = PressSessionNotice.NumericLimit) }
            return PressAcceptance.NumericLimit
        }
        val acceptance =
            when (val result = inputQueue.accept(emotion, wallClock(), nowMillis())) {
                PressQueueAcceptance.NumericLimit -> {
                    mutableState.update { it.copy(notice = PressSessionNotice.NumericLimit) }
                    return PressAcceptance.NumericLimit
                }

                is PressQueueAcceptance.Accepted -> {
                    result
                }
            }
        writeSession.accept(emotion)
        if (pausedNotice == PressSessionNotice.Rejected) pausedNotice = null
        publish(
            mutableState.value.copy(
                notice =
                    when {
                        writeSession.isOutcomeUnknown() -> PressSessionNotice.OutcomeUnknown
                        pausedNotice != null -> pausedNotice
                        connected == false -> PressSessionNotice.RetryRequired
                        else -> null
                    },
            ),
        )
        if (acceptance.isThresholdReached) flushRequests.trySend(Unit)
        scheduleSend(acceptance.delayMillis)
        return PressAcceptance.Accepted
    }

    /** 화면 복귀나 연결 복구 시 통계를 확인하고 보류 중인 쓰기를 재개한다. */
    fun onForeground() {
        if (!hasStarted && !writeSession.hasOutstanding()) return
        refreshToday()
        retryUnsent()
    }

    /** 통계 캐시가 오래됐으면 조회하고, 진행 중인 조회가 있으면 추가 조회를 하나로 합친다. */
    fun refreshToday(force: Boolean = false) {
        hasStarted = true
        reads.refreshToday(force, writeWorkerActive = workerJob?.isActive == true)
    }

    /** 재전송 조건을 확인한 뒤 보류 중인 쓰기를 재개한다. */
    fun retryUnsent() {
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (writeSession.isOutcomeUnknown() && !canRetryUnknown()) {
            publish(mutableState.value.copy(notice = PressSessionNotice.OutcomeUnknown))
            return
        }
        if (writeSession.hasOutstanding()) scheduleSend(PressDelayMillis.Zero)
    }

    /** 서버가 보장한 멱등성 유효 시간 안에서만 응답 불명 요청을 재전송할 수 있는지 판단한다. */
    private fun canRetryUnknown(): Boolean =
        writeSession.canRetryUnknown(
            nowMillis(),
            sender.idempotencyWindowMillis?.takeIf { it >= 0L }?.let { PressDelayMillis(it) },
        )

    /** 조회 후 미전송 입력은 최초 대기의 남은 시간만 기다리고, 고정된 쓰기는 재개한다. */
    private fun resumeQueuedWork() {
        val now = nowMillis()
        val wait = inputQueue.resumeDelay(writeSession.hasPendingWrite(), now)
        scheduleSend(wait)
    }

    /** 현재 묶음의 최초 입력 시각을 기준으로 남은 대기 시간을 돌려준다. */
    private fun remainingBatchWait(now: PressMonotonicMillis = nowMillis()): PressDelayMillis =
        inputQueue.remainingWait(now)

    /** 전송 작업을 하나만 예약하고 종료 시 대기 중인 조회나 쓰기를 이어준다. */
    private fun scheduleSend(delayMillis: PressDelayMillis) {
        if (workerJob?.isActive == true || connected == false || !writeSession.hasOutstanding()) return
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (writeSession.isOutcomeUnknown() && !canRetryUnknown()) return
        lateinit var launchedWorker: Job
        launchedWorker =
            sessionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(PressFlushPolicy.remainingWait(sendRetryDeadline, nowMillis()).value)
                    awaitFlush(delayMillis)
                    sendAvailable()
                } finally {
                    if (workerJob === launchedWorker) workerJob = null
                    if (!reads.resumeRequestedRead() && writeSession.hasOutstanding() &&
                        sessionScope.coroutineContext[Job]?.isActive != false
                    ) {
                        resumeQueuedWork()
                    }
                }
            }
        workerJob = launchedWorker
        launchedWorker.start()
    }

    /** 보류 쓰기를 순서대로 전송하고 응답 결과에 맞춰 확정·거절·재시도·조회 정산한다. */
    private suspend fun sendAvailable() {
        while (writeSession.hasOutstanding() && connected != false) {
            if (writeSession.isOutcomeUnknown() && !canRetryUnknown()) return

            reads.awaitCurrentRead()
            if (writeSession.isAwaitingSnapshot() || mutableState.value.myToday == null) {
                reads.readNow()
                if (writeSession.isAwaitingSnapshot() || mutableState.value.myToday == null) {
                    scheduleRetry()
                    return
                }
            }
            if (connected == false) return
            val write =
                writeSession.prepareForSend(checkNotNull(mutableState.value.myToday), ::newWrite) ?: return
            publish(mutableState.value.copy(isSending = true))
            val result =
                try {
                    sender.send(write.pending.batch)
                } catch (cancelled: CancellationException) {
                    writeSession.markRetryPending(write, uncertain = true)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    if (canRetryUnknown()) scheduleRetry()
                    throw cancelled
                } catch (_: Exception) {
                    PressSendResult.OutcomeUnknown
                }
            when (result) {
                is PressSendResult.Accepted -> {
                    runCatching {
                        monitoring.recordSuccessfulActivity(
                            ActivityType.PERSONAL_PRESS,
                            write.pending.batch.occurredAt,
                        )
                    }
                    val currentPersonalSnapshot = checkNotNull(mutableState.value.myToday)
                    val updatedPersonalSnapshot =
                        currentPersonalSnapshot.copy(counts = result.today.counts, total = result.today.total)
                    writeSession.confirm(write)
                    sendFailureCount = 0
                    pausedNotice = null
                    reads.personalWriteSucceeded()
                    publish(
                        mutableState.value.copy(
                            myToday = updatedPersonalSnapshot,
                            isSending = false,
                            hasMyError = false,
                            notice = null,
                        ),
                    )
                }

                PressSendResult.Rejected -> {
                    writeSession.reject(write)
                    sendFailureCount = 0
                    pausedNotice = PressSessionNotice.Rejected
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                }

                PressSendResult.NotSent, is PressSendResult.RetryAfter -> {
                    writeSession.markRetryPending(write)
                    pausedNotice =
                        if (write.uncertain) PressSessionNotice.OutcomeUnknown else PressSessionNotice.RetryRequired
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    val retryAfterMillis = (result as? PressSendResult.RetryAfter)?.delayMillis
                    scheduleRetry(retryAfterMillis?.let { PressDelayMillis(it) } ?: PressDelayMillis.Zero)
                    return
                }

                PressSendResult.ContractPending -> {
                    writeSession.markRetryPending(write)
                    pausedNotice = PressSessionNotice.WaitingForApiContract
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    return
                }

                PressSendResult.OutcomeUnknown -> {
                    writeSession.markAwaitingSnapshot(write)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    reads.readNow()
                    if (!writeSession.hasPendingWrite()) reads.refreshAggregateIfPending()
                    if (writeSession.isOutcomeUnknown() && canRetryUnknown()) scheduleRetry()
                    return
                }
            }
            if (writeSession.hasOutstanding()) {
                delay(retryPolicy.minRequestIntervalMillis.value)
                awaitFlush(remainingBatchWait())
            }
        }
        if (!writeSession.hasOutstanding()) reads.scheduleAggregateRefresh()
    }

    /** 묶음 대기 시간이 끝나거나 임계값 신호가 오면 전송 작업을 깨운다. */
    private suspend fun awaitFlush(delayMillis: PressDelayMillis) {
        if (delayMillis.value > 0L && !inputQueue.isReadyToFlush()) {
            withTimeoutOrNull(delayMillis.value) { flushRequests.receive() }
        }
        flushRequests.tryReceive()
    }

    /** 다음 묶음과 정산 기준 스냅샷을 하나의 보류 쓰기로 만든다. */
    private fun newWrite(): PressWriteState? {
        val nextBatch = inputQueue.takeBatch() ?: return null
        return PressWriteState(PressPendingWrite(nextBatch, checkNotNull(mutableState.value.myToday)), nowMillis())
    }

    /** 실패 횟수와 서버가 준 Retry-After를 반영해 다음 전송을 예약한다. */
    private fun scheduleRetry(retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero) {
        sendFailureCount = (sendFailureCount + 1).coerceAtMost(64)
        val wait = retryPolicy.delayMillis(sendFailureCount, retryAfterMillis)
        sendRetryDeadline = PressFlushPolicy.deadline(nowMillis(), wait)
        retryJob?.cancel()
        retryJob =
            sessionScope.launch {
                delay(wait.value)
                workerJob?.join()
                scheduleSend(PressDelayMillis.Zero)
            }
    }

    /** 조회 담당 객체의 결과를 정산하고 공개 상태에 반영한다. */
    private fun handleReadEvent(event: PressReadEvent) {
        when (event) {
            PressReadEvent.PairStarted -> {
                mutableState.update {
                    it.copy(isLoadingMy = true, isLoadingAll = true, hasMyError = false, hasAllError = false)
                }
            }

            is PressReadEvent.PairCompleted -> {
                applyPairedRead(event)
            }

            PressReadEvent.PairIdle -> {
                if (writeSession.hasOutstanding()) resumeQueuedWork()
            }

            PressReadEvent.AggregateStarted -> {
                publish(mutableState.value.copy(isLoadingAll = true, hasAllError = false))
            }

            is PressReadEvent.AggregateCompleted -> {
                applyAggregateRead(event)
            }

            PressReadEvent.AggregateDiscarded -> {
                publish(mutableState.value.copy(isLoadingAll = false))
            }

            PressReadEvent.PairRefreshRequired -> {
                publish(mutableState.value.copy(isLoadingAll = false))
                refreshToday(force = true)
            }
        }
    }

    /** 조회 시작 당시의 쓰기 문맥을 기준으로 개인·전체 스냅샷을 정산한다. */
    private fun applyPairedRead(event: PressReadEvent.PairCompleted) {
        val writeOutcomeUnknownAtReadStart = event.context.writeOutcomeUnknown
        val acceptedPressesCoveredAtReadStart = event.context.acceptedPressCoverage
        val personalSnapshot = event.personal
        val aggregateSnapshot = event.aggregate
        if (writeOutcomeUnknownAtReadStart && personalSnapshot != null) {
            when (writeSession.resolveUnknown(personalSnapshot, canRetryUnknown())) {
                PressWriteResolution.Confirmed -> {
                    reads.markAggregateRefreshPending()
                    sendFailureCount = 0
                    retryJob?.cancel()
                    pausedNotice = null
                }

                PressWriteResolution.Retired -> {
                    pausedNotice = null
                    sendFailureCount = 0
                    retryJob?.cancel()
                }

                is PressWriteResolution.Pending, null -> {
                }
            }
        }
        val readablePersonalSnapshot =
            personalSnapshot?.takeIf {
                !writeSession.isOutcomeUnknown() && !writeSession.isAwaitingSnapshot()
            }
        val displayablePersonalSnapshot =
            readablePersonalSnapshot?.takeIf { writeSession.canDisplayMyTotal(it.total) }
        val reconcilableAggregateSnapshot = if (writeOutcomeUnknownAtReadStart) null else aggregateSnapshot
        if (reconcilableAggregateSnapshot != null) {
            reconcileAllSnapshot(reconcilableAggregateSnapshot, acceptedPressesCoveredAtReadStart)
        }
        val displayableAggregateSnapshot =
            reconcilableAggregateSnapshot?.takeIf { writeSession.canDisplayAllTotal(it.total) }
        publish(
            mutableState.value.copy(
                myToday = displayablePersonalSnapshot ?: mutableState.value.myToday,
                allToday = displayableAggregateSnapshot ?: mutableState.value.allToday,
                isLoadingMy = false,
                isLoadingAll = false,
                hasMyError =
                    personalSnapshot == null ||
                        (readablePersonalSnapshot != null && displayablePersonalSnapshot == null),
                hasAllError =
                    reconcilableAggregateSnapshot == null ||
                        !writeSession.canDisplayAllTotal(reconcilableAggregateSnapshot.total),
                notice = if (writeSession.isOutcomeUnknown()) PressSessionNotice.OutcomeUnknown else pausedNotice,
            ),
        )
        reads.completePairedRead(
            personalApplied = displayablePersonalSnapshot != null,
            aggregateApplied = displayableAggregateSnapshot != null,
        )
    }

    /** 적용 가능한 전체 집계를 정산하고 조회 성공 여부를 담당 객체에 전달한다. */
    private fun applyAggregateRead(event: PressReadEvent.AggregateCompleted) {
        val snapshot = event.snapshot
        val applied =
            snapshot != null &&
                reconcileAllSnapshot(snapshot, event.context.acceptedPressCoverage) &&
                writeSession.canDisplayAllTotal(snapshot.total)
        publish(
            mutableState.value.copy(
                allToday = if (applied) snapshot else mutableState.value.allToday,
                isLoadingAll = false,
                hasAllError = !applied,
            ),
        )
        reads.completeAggregateRead(applied)
    }

    /** 쓰기 세션의 낙관적 숫자와 처리 상태를 화면에 공개할 값에 합친다. */
    private fun publish(base: PressSessionState) {
        val counts = writeSession.counts()
        reads.updateWriteContext(
            PressReadContext(
                writeOutcomeUnknown = writeSession.isOutcomeUnknown(),
                writeInFlight = writeSession.isInFlight(),
                acceptedPressCoverage = writeSession.acceptedPressCoverageAtReadStart(),
                personalPressDate = base.myToday?.pressDate,
            ),
        )
        mutableState.value =
            base.copy(
                optimisticCounts = counts.optimisticCounts,
                optimisticAllPressCount = counts.optimisticAllPressCount,
                pendingPressCount = counts.pendingPressCount,
                isOutcomeUnknown = writeSession.isOutcomeUnknown(),
            )
    }

    /** 조회 시작 시점까지 서버 반영이 확인된 입력만 전체 집계와 대조한다. */
    private fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedPressesCoveredAtReadStart: Long,
    ): Boolean = writeSession.reconcileAllSnapshot(snapshot, acceptedPressesCoveredAtReadStart)
}
