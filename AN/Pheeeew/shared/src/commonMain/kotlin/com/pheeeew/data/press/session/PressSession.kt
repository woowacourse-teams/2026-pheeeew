package com.pheeeew.data.press.session

import com.pheeeew.core.monitoring.ActivityType
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.data.press.delivery.PressWriteState
import com.pheeeew.data.press.read.PressAggregateReadPolicy
import com.pheeeew.data.press.read.PressReadState
import com.pheeeew.data.press.read.PressStatisticsReader
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
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
import com.pheeeew.domain.repository.PressReadResult
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

/** Coordinates queue, writes, reads, and public session-state publication on the owner dispatcher. */
internal class PressSession(
    statistics: PressStatisticsDataSource,
    private val sender: PressSender,
    private val sessionScope: CoroutineScope,
    batchMaxPerEmotion: Int,
    batchMaxTotal: Int,
    flushDelayMillis: PressDelayMillis,
    private val retryPolicy: PressRetryPolicy,
    private val aggregateRefreshMillis: PressDelayMillis,
    connectivity: Flow<Boolean>?,
    private val nowMillis: () -> PressMonotonicMillis,
    private val monitoring: Monitoring,
    private val wallClock: () -> PressEpochMillis,
) {
    private val statisticsReader = PressStatisticsReader(statistics)
    private val mutableState = MutableStateFlow(PressSessionState())
    val state = mutableState.asStateFlow()
    private val inputQueue = PressInputQueue(batchMaxPerEmotion, batchMaxTotal, flushDelayMillis)
    private val writeSession = PressWriteSession()
    private val readState = PressReadState()
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private var workerJob: Job? = null
    private var readJob: Job? = null
    private var retryJob: Job? = null
    private var statisticsRetryJob: Job? = null
    private var hasStarted = false
    private var pausedNotice: PressSessionNotice? = null
    private var connected: Boolean? = null
    private var sendFailureCount = 0
    private var sendRetryDeadline = PressMonotonicMillis(0L)
    private var aggregateRefreshJob: Job? = null

    init {
        require(aggregateRefreshMillis.value > 0L)
        connectivity?.let { updates ->
            sessionScope.launch {
                updates.distinctUntilChanged().collect { available ->
                    connected = available
                    if (available) {
                        onForeground()
                    } else if (writeSession.hasOutstanding() && pausedNotice == null) {
                        publish(mutableState.value.copy(notice = PressSessionNotice.RetryRequired))
                    }
                }
            }
        }
    }

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

    fun onForeground() {
        if (!hasStarted && !writeSession.hasOutstanding()) return
        refreshToday()
        retryUnsent()
    }

    fun refreshToday(force: Boolean = false) {
        hasStarted = true
        if (!force && hasFreshMySnapshot() && hasFreshAllSnapshot()) return
        if (workerJob?.isActive == true || readJob?.isActive == true) {
            readState.requestAnotherRead()
        } else {
            launchRead()
        }
    }

    fun retryUnsent() {
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (writeSession.isOutcomeUnknown() && !canRetryUnknown()) {
            publish(mutableState.value.copy(notice = PressSessionNotice.OutcomeUnknown))
            return
        }
        if (writeSession.hasOutstanding()) scheduleSend(PressDelayMillis.Zero)
    }

    private fun canRetryUnknown(): Boolean =
        writeSession.canRetryUnknown(
            nowMillis(),
            sender.idempotencyWindowMillis?.takeIf { it >= 0L }?.let { PressDelayMillis(it) },
        )

    /** Completing a read is not a retry: fresh input keeps the wait established on acceptance. */
    private fun resumeQueuedWork() {
        val now = nowMillis()
        val wait = inputQueue.resumeDelay(writeSession.hasPendingWrite(), now)
        scheduleSend(wait)
    }

    private fun remainingBatchWait(now: PressMonotonicMillis = nowMillis()): PressDelayMillis =
        inputQueue.remainingWait(now)

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
                    if (readState.consumeAnotherRead(sessionScope.coroutineContext[Job]?.isActive != false)) {
                        launchRead()
                    } else if (writeSession.hasOutstanding() && sessionScope.coroutineContext[Job]?.isActive != false) {
                        resumeQueuedWork()
                    }
                }
            }
        workerJob = launchedWorker
        launchedWorker.start()
    }

    private suspend fun sendAvailable() {
        while (writeSession.hasOutstanding() && connected != false) {
            if (writeSession.isOutcomeUnknown() && !canRetryUnknown()) return
            readJob?.join()
            if (writeSession.isAwaitingSnapshot() || mutableState.value.myToday == null) {
                readAndReconcile()
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
                    // Cancellation does not prove the server didn't apply the request.
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
                    readState.personalWriteSucceeded(nowMillis())
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
                    // The server may have committed this write. Reconcile with the aggregate API, but
                    // never replay it without a server-supported idempotency key.
                    writeSession.markAwaitingSnapshot(write)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    readAndReconcile()
                    if (!writeSession.hasPendingWrite() &&
                        readState.hasAggregateRefreshPending()
                    ) {
                        refreshAllAfterWrite()
                    }
                    if (writeSession.isOutcomeUnknown() && canRetryUnknown()) scheduleRetry()
                    return
                }
            }
            if (writeSession.hasOutstanding()) {
                delay(retryPolicy.minRequestIntervalMillis.value)
                awaitFlush(remainingBatchWait())
            }
        }
        if (!writeSession.hasOutstanding() && readState.hasAggregateRefreshPending()) scheduleAggregateRefresh()
    }

    private suspend fun awaitFlush(delayMillis: PressDelayMillis) {
        if (delayMillis.value > 0L && !inputQueue.isReadyToFlush()) {
            withTimeoutOrNull(delayMillis.value) { flushRequests.receive() }
        }
        flushRequests.tryReceive()
    }

    private fun newWrite(): PressWriteState? {
        val nextBatch = inputQueue.takeBatch() ?: return null
        return PressWriteState(PressPendingWrite(nextBatch, checkNotNull(mutableState.value.myToday)), nowMillis())
    }

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

    private fun launchRead() {
        if (readJob?.isActive == true) {
            readState.requestAnotherRead()
            return
        }
        lateinit var launchedRead: Job
        launchedRead =
            sessionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    readAndReconcile()
                } finally {
                    if (readJob === launchedRead) readJob = null
                    if (readState.consumeAnotherRead(sessionScope.coroutineContext[Job]?.isActive != false)) {
                        launchRead()
                    } else if (writeSession.hasOutstanding()) {
                        resumeQueuedWork()
                    }
                }
            }
        readJob = launchedRead
        launchedRead.start()
    }

    private suspend fun readAndReconcile() {
        val writeOutcomeUnknownAtReadStart = writeSession.isOutcomeUnknown()
        val acceptedPressesCoveredAtReadStart = writeSession.acceptedPressCoverageAtReadStart()
        val readGeneration = readState.beginRead()
        mutableState.update {
            it.copy(isLoadingMy = true, isLoadingAll = true, hasMyError = false, hasAllError = false)
        }
        val initialStatisticsPair = statisticsReader.readPair()
        val consistentStatisticsPair =
            if (initialStatisticsPair.hasDifferentDates()) statisticsReader.readPair() else initialStatisticsPair
        if (readState.isStale(readGeneration)) return
        val statisticsDatesMatch = !consistentStatisticsPair.hasDifferentDates()
        val personalSnapshot =
            (consistentStatisticsPair.personal as? PressReadResult.Loaded)?.value?.takeIf { statisticsDatesMatch }
        val aggregateSnapshot =
            (consistentStatisticsPair.aggregate as? PressReadResult.Loaded)?.value?.takeIf { statisticsDatesMatch }
        if (writeOutcomeUnknownAtReadStart && personalSnapshot != null) {
            when (writeSession.resolveUnknown(personalSnapshot, canRetryUnknown())) {
                PressWriteResolution.Confirmed -> {
                    readState.markAggregateRefreshPending()
                    sendFailureCount = 0
                    retryJob?.cancel()
                    pausedNotice = null
                }

                PressWriteResolution.Retired -> {
                    // Drop only the ambiguous batch without replay; new input may proceed.
                    pausedNotice = null
                    sendFailureCount = 0
                    retryJob?.cancel()
                }

                is PressWriteResolution.Pending, null -> {
                }
            }
        }
        // Freeze the personal baseline until a pending write is covered; partial/stale reads must not double-count it.
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
        val snapshotReadCompletedAt = nowMillis()
        if (displayablePersonalSnapshot != null) {
            readState.recordMySnapshotSuccess(snapshotReadCompletedAt)
        }
        if (reconcilableAggregateSnapshot != null &&
            writeSession.canDisplayAllTotal(reconcilableAggregateSnapshot.total)
        ) {
            readState.recordAllSnapshotSuccess(snapshotReadCompletedAt)
        }
        if (displayablePersonalSnapshot != null && displayableAggregateSnapshot != null) {
            readState.resetStatisticsFailures()
            statisticsRetryJob?.cancel()
            statisticsRetryJob = null
        } else {
            scheduleStatisticsRetry()
        }
    }

    private fun scheduleAggregateRefresh() {
        if (aggregateRefreshJob?.isActive == true) return
        aggregateRefreshJob =
            sessionScope.launch {
                while (
                    readState.hasAggregateRefreshPending() &&
                    connected != false &&
                    !writeSession.isOutcomeUnknown()
                ) {
                    delay(aggregateRefreshMillis.value)
                    if (!writeSession.isInFlight()) {
                        refreshAllAfterWrite(detached = true)
                    }
                }
            }
    }

    private suspend fun refreshAllAfterWrite(detached: Boolean = false) {
        readState.clearAggregateRefreshPending()
        val acceptedPressesCoveredAtReadStart = writeSession.acceptedPressCoverageAtReadStart()
        val readGeneration = readState.currentGeneration()
        publish(mutableState.value.copy(isLoadingAll = true, hasAllError = false))
        when (val aggregateRead = statisticsReader.readAggregate()) {
            is PressReadResult.Loaded -> {
                val aggregateSnapshot = aggregateRead.value
                if (detached &&
                    !PressAggregateReadPolicy.canApply(
                        readGeneration,
                        readState.currentGeneration(),
                        writeSession.isInFlight(),
                        writeSession.isOutcomeUnknown(),
                    )
                ) {
                    // A write or paired read superseded this snapshot. Never subtract its old coverage.
                    readState.markAggregateRefreshPending()
                    publish(mutableState.value.copy(isLoadingAll = false))
                    return
                }
                val personalPressDate = mutableState.value.myToday?.pressDate
                if (personalPressDate != null && personalPressDate != aggregateSnapshot.pressDate) {
                    if (detached) {
                        publish(mutableState.value.copy(isLoadingAll = false))
                        refreshToday(force = true)
                    } else {
                        readAndReconcile()
                    }
                } else {
                    if (reconcileAllSnapshot(aggregateSnapshot, acceptedPressesCoveredAtReadStart) &&
                        writeSession.canDisplayAllTotal(aggregateSnapshot.total)
                    ) {
                        publish(
                            mutableState.value.copy(
                                allToday = aggregateSnapshot,
                                isLoadingAll = false,
                                hasAllError = false,
                            ),
                        )
                        readState.recordAllSnapshotSuccess(nowMillis())
                        readState.resetStatisticsFailures()
                        statisticsRetryJob?.cancel()
                        statisticsRetryJob = null
                    } else {
                        publish(mutableState.value.copy(isLoadingAll = false, hasAllError = true))
                        readState.invalidateAllSnapshot()
                        scheduleStatisticsRetry()
                    }
                }
            }

            PressReadResult.Unavailable -> {
                publish(mutableState.value.copy(isLoadingAll = false, hasAllError = true))
                readState.invalidateAllSnapshot()
                scheduleStatisticsRetry()
            }
        }
    }

    private fun scheduleStatisticsRetry() {
        if (statisticsRetryJob?.isActive == true) return
        val wait = readState.nextStatisticsRetryDelay(retryPolicy)
        statisticsRetryJob =
            sessionScope.launch {
                delay(wait.value)
                statisticsRetryJob = null
                if (connected != false) launchRead()
            }
    }

    private fun hasFreshMySnapshot(): Boolean =
        readState.hasFreshMySnapshot(nowMillis(), PressDelayMillis(STATISTICS_TTL_MILLIS))

    private fun hasFreshAllSnapshot(): Boolean =
        readState.hasFreshAllSnapshot(nowMillis(), PressDelayMillis(STATISTICS_TTL_MILLIS))

    private fun publish(base: PressSessionState) {
        val counts = writeSession.counts()
        mutableState.value =
            base.copy(
                optimisticCounts = counts.optimisticCounts,
                optimisticAllPressCount = counts.optimisticAllPressCount,
                pendingPressCount = counts.pendingPressCount,
                isOutcomeUnknown = writeSession.isOutcomeUnknown(),
            )
    }

    /** Moves only acknowledged writes covered by this snapshot into the server baseline. */
    private fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedPressesCoveredAtReadStart: Long,
    ): Boolean = writeSession.reconcileAllSnapshot(snapshot, acceptedPressesCoveredAtReadStart)

    private companion object {
        const val STATISTICS_TTL_MILLIS = 5_000L
    }
}
