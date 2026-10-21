package com.pheeeew.data.repository.press

import com.pheeeew.core.monitoring.ActivityType
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.domain.repository.PressSender
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Coordinates queue, writes, reads, and public session-state publication on the owner dispatcher. */
internal class PressRepositoryImpl(
    private val statistics: PressStatisticsDataSource,
    private val sender: PressSender,
    private val sessionScope: CoroutineScope,
    batchMaxPerEmotion: Int = 30,
    batchMaxTotal: Int = 100,
    flushDelayMillis: PressDelayMillis = PressDelayMillis(500L),
    private val retryPolicy: PressRetryPolicy = PressRetryPolicy(),
    private val aggregateRefreshMillis: PressDelayMillis = PressDelayMillis(1_000L),
    connectivity: Flow<Boolean>? = null,
    private val nowMillis: () -> PressMonotonicMillis = monotonicClock(),
    private val monitoring: Monitoring = NoOpMonitoring,
    private val wallClock: () -> PressEpochMillis = {
        PressEpochMillis(Clock.System.now().toEpochMilliseconds())
    },
) : PressRepository {
    private val mutableState = MutableStateFlow(PressSessionState())
    override val state = mutableState.asStateFlow()
    private val inputQueue = PressInputQueue(batchMaxPerEmotion, batchMaxTotal, flushDelayMillis)
    private val writeLedger = PressWriteLedger()
    private val readState = PressReadState()
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private var workerJob: Job? = null
    private var readJob: Job? = null
    private var retryJob: Job? = null
    private var statisticsRetryJob: Job? = null
    private var hasStarted = false
    private var pausedNotice: PressSessionNotice? = null
    private var connected: Boolean? = null
    private var failures = 0
    private var retryNotBefore = PressMonotonicMillis(0L)
    private var aggregateRefreshJob: Job? = null

    init {
        require(aggregateRefreshMillis.value > 0L)
        connectivity?.let { updates ->
            sessionScope.launch {
                updates.distinctUntilChanged().collect { available ->
                    connected = available
                    if (available) {
                        onForeground()
                    } else if (writeLedger.hasOutstanding() && pausedNotice == null) {
                        publish(mutableState.value.copy(notice = PressSessionNotice.RetryRequired))
                    }
                }
            }
        }
    }

    override fun accept(emotion: EmotionState): PressAcceptance {
        val serverTotal = mutableState.value.myToday?.total ?: 0L
        val allServerTotal = mutableState.value.allToday?.total ?: 0L
        if (writeLedger.exceedsAcceptanceLimit(serverTotal, allServerTotal)) {
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
        writeLedger.accept(emotion)
        if (pausedNotice == PressSessionNotice.Rejected) pausedNotice = null
        publish(
            mutableState.value.copy(
                notice =
                    when {
                        writeLedger.isOutcomeUnknown() -> PressSessionNotice.OutcomeUnknown
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

    override fun onForeground() {
        if (!hasStarted && !writeLedger.hasOutstanding()) return
        refreshToday()
        retryUnsent()
    }

    override fun refreshToday(force: Boolean) {
        hasStarted = true
        if (!force && hasFreshMySnapshot() && hasFreshAllSnapshot()) return
        if (workerJob?.isActive == true || readJob?.isActive == true) {
            readState.requestAnotherRead()
        } else {
            launchRead()
        }
    }

    override fun retryUnsent() {
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (writeLedger.isOutcomeUnknown() && !canRetryUnknown()) {
            publish(mutableState.value.copy(notice = PressSessionNotice.OutcomeUnknown))
            return
        }
        if (writeLedger.hasOutstanding()) scheduleSend(PressDelayMillis.Zero)
    }

    private fun canRetryUnknown(): Boolean =
        writeLedger.canRetryUnknown(
            nowMillis(),
            sender.idempotencyWindowMillis?.takeIf { it >= 0L }?.let { PressDelayMillis(it) },
        )

    /** Completing a read is not a retry: fresh input keeps the wait established on acceptance. */
    private fun resumeQueuedWork() {
        val now = nowMillis()
        val wait = inputQueue.resumeDelay(writeLedger.hasPendingWrite(), now)
        scheduleSend(wait)
    }

    private fun remainingBatchWait(now: PressMonotonicMillis = nowMillis()): PressDelayMillis =
        inputQueue.remainingWait(now)

    private fun scheduleSend(delayMillis: PressDelayMillis) {
        if (workerJob?.isActive == true || connected == false || !writeLedger.hasOutstanding()) return
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (writeLedger.isOutcomeUnknown() && !canRetryUnknown()) return
        lateinit var launchedWorker: Job
        launchedWorker =
            sessionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(PressFlushPolicy.remainingWait(retryNotBefore, nowMillis()).value)
                    awaitFlush(delayMillis)
                    sendAvailable()
                } finally {
                    if (workerJob === launchedWorker) workerJob = null
                    if (readState.consumeAnotherRead(sessionScope.coroutineContext[Job]?.isActive != false)) {
                        launchRead()
                    } else if (writeLedger.hasOutstanding() && sessionScope.coroutineContext[Job]?.isActive != false) {
                        resumeQueuedWork()
                    }
                }
            }
        workerJob = launchedWorker
        launchedWorker.start()
    }

    private suspend fun sendAvailable() {
        while (writeLedger.hasOutstanding() && connected != false) {
            if (writeLedger.isOutcomeUnknown() && !canRetryUnknown()) return
            readJob?.join()
            if (writeLedger.isAwaitingSnapshot() || mutableState.value.myToday == null) {
                readAndReconcile()
                if (writeLedger.isAwaitingSnapshot() || mutableState.value.myToday == null) {
                    scheduleRetry()
                    return
                }
            }
            if (connected == false) return
            val write =
                writeLedger.prepareForSend(checkNotNull(mutableState.value.myToday), ::newWrite) ?: return
            publish(mutableState.value.copy(isSending = true))
            val result =
                try {
                    sender.send(write.batch)
                } catch (cancelled: CancellationException) {
                    // Cancellation does not prove the server didn't apply the request.
                    writeLedger.markRetryPending(write, uncertain = true)
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
                            write.batch.occurredAt,
                        )
                    }
                    val current = checkNotNull(mutableState.value.myToday)
                    val updated = current.copy(counts = result.today.counts, total = result.today.total)
                    writeLedger.confirm(write)
                    failures = 0
                    pausedNotice = null
                    readState.personalWriteSucceeded(nowMillis())
                    publish(
                        mutableState.value.copy(
                            myToday = updated,
                            isSending = false,
                            hasMyError = false,
                            notice = null,
                        ),
                    )
                }

                PressSendResult.Rejected -> {
                    writeLedger.reject(write)
                    failures = 0
                    pausedNotice = PressSessionNotice.Rejected
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                }

                PressSendResult.NotSent, is PressSendResult.RetryAfter -> {
                    writeLedger.markRetryPending(write)
                    pausedNotice =
                        if (write.uncertain) PressSessionNotice.OutcomeUnknown else PressSessionNotice.RetryRequired
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    val retryAfterMillis = (result as? PressSendResult.RetryAfter)?.delayMillis
                    scheduleRetry(retryAfterMillis?.let { PressDelayMillis(it) } ?: PressDelayMillis.Zero)
                    return
                }

                PressSendResult.ContractPending -> {
                    writeLedger.markRetryPending(write)
                    pausedNotice = PressSessionNotice.WaitingForApiContract
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    return
                }

                PressSendResult.OutcomeUnknown -> {
                    // The server may have committed this write. Reconcile with the aggregate API, but
                    // never replay it without a server-supported idempotency key.
                    writeLedger.markAwaitingSnapshot(write)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    readAndReconcile()
                    if (!writeLedger.hasPendingWrite() && readState.hasAggregateRefreshPending()) refreshAllAfterWrite()
                    if (writeLedger.isOutcomeUnknown() && canRetryUnknown()) scheduleRetry()
                    return
                }
            }
            if (writeLedger.hasOutstanding()) {
                delay(retryPolicy.minRequestIntervalMillis.value)
                awaitFlush(remainingBatchWait())
            }
        }
        if (!writeLedger.hasOutstanding() && readState.hasAggregateRefreshPending()) scheduleAggregateRefresh()
    }

    private suspend fun awaitFlush(delayMillis: PressDelayMillis) {
        if (delayMillis.value > 0L && !inputQueue.isReadyToFlush()) {
            withTimeoutOrNull(delayMillis.value) { flushRequests.receive() }
        }
        flushRequests.tryReceive()
    }

    private fun newWrite(): PressWriteState? {
        val next = inputQueue.takeBatch() ?: return null
        return PressWriteState(next, checkNotNull(mutableState.value.myToday), nowMillis())
    }

    private fun scheduleRetry(retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero) {
        failures = (failures + 1).coerceAtMost(64)
        val wait = retryPolicy.delayMillis(failures, retryAfterMillis)
        retryNotBefore = PressFlushPolicy.deadline(nowMillis(), wait)
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
                    } else if (writeLedger.hasOutstanding()) {
                        resumeQueuedWork()
                    }
                }
            }
        readJob = launchedRead
        launchedRead.start()
    }

    private suspend fun readAndReconcile() {
        val uncertainAtReadStart = writeLedger.isOutcomeUnknown()
        val acceptedCoveredAtReadStart = writeLedger.acceptedCoverageAtReadStart()
        val generation = readState.beginRead()
        mutableState.update {
            it.copy(isLoadingMy = true, isLoadingAll = true, hasMyError = false, hasAllError = false)
        }
        val first = fetchPair()
        val pair = if (first.hasDifferentDates()) fetchPair() else first
        if (readState.isStale(generation)) return
        val datesMatch = !pair.hasDifferentDates()
        val my = (pair.my as? PressReadResult.Loaded)?.value?.takeIf { datesMatch }
        val all = (pair.all as? PressReadResult.Loaded)?.value?.takeIf { datesMatch }
        if (uncertainAtReadStart && my != null) {
            when (writeLedger.resolveUnknown(my, canRetryUnknown())) {
                PressWriteResolution.Confirmed -> {
                    readState.markAggregateRefreshPending()
                    failures = 0
                    retryJob?.cancel()
                    pausedNotice = null
                }

                PressWriteResolution.Retired -> {
                    // Drop only the ambiguous batch without replay; new input may proceed.
                    pausedNotice = null
                    failures = 0
                    retryJob?.cancel()
                }

                is PressWriteResolution.Pending, null -> {
                }
            }
        }
        // Freeze the personal baseline until a pending write is covered; partial/stale reads must not double-count it.
        val readableMy =
            my?.takeIf {
                !writeLedger.isOutcomeUnknown() && !writeLedger.isAwaitingSnapshot()
            }
        val safeMy = readableMy?.takeIf { writeLedger.canDisplayMyTotal(it.total) }
        val safeAll = if (uncertainAtReadStart) null else all
        if (safeAll != null) reconcileAllSnapshot(safeAll, acceptedCoveredAtReadStart)
        val displayableAll =
            safeAll?.takeIf { writeLedger.canDisplayAllTotal(it.total) }
        publish(
            mutableState.value.copy(
                myToday = safeMy ?: mutableState.value.myToday,
                allToday = displayableAll ?: mutableState.value.allToday,
                isLoadingMy = false,
                isLoadingAll = false,
                hasMyError = my == null || (readableMy != null && safeMy == null),
                hasAllError = safeAll == null || !writeLedger.canDisplayAllTotal(safeAll.total),
                notice = if (writeLedger.isOutcomeUnknown()) PressSessionNotice.OutcomeUnknown else pausedNotice,
            ),
        )
        val readAt = nowMillis()
        if (safeMy != null) {
            readState.recordMySnapshotSuccess(readAt)
        }
        if (safeAll != null && writeLedger.canDisplayAllTotal(safeAll.total)) {
            readState.recordAllSnapshotSuccess(readAt)
        }
        if (safeMy != null && displayableAll != null) {
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
                    !writeLedger.isOutcomeUnknown()
                ) {
                    delay(aggregateRefreshMillis.value)
                    if (!writeLedger.isInFlight()) {
                        refreshAllAfterWrite(detached = true)
                    }
                }
            }
    }

    private suspend fun refreshAllAfterWrite(detached: Boolean = false) {
        readState.clearAggregateRefreshPending()
        val acceptedCoveredAtReadStart = writeLedger.acceptedCoverageAtReadStart()
        val generation = readState.currentGeneration()
        publish(mutableState.value.copy(isLoadingAll = true, hasAllError = false))
        when (val result = readSafely(statistics::findAllToday)) {
            is PressReadResult.Loaded -> {
                val all = result.value
                if (detached && !writeLedger.canApplyAggregate(generation, readState.currentGeneration())) {
                    // A write or paired read superseded this snapshot. Never subtract its old coverage.
                    readState.markAggregateRefreshPending()
                    publish(mutableState.value.copy(isLoadingAll = false))
                    return
                }
                val myDate = mutableState.value.myToday?.pressDate
                if (myDate != null && myDate != all.pressDate) {
                    if (detached) {
                        publish(mutableState.value.copy(isLoadingAll = false))
                        refreshToday(force = true)
                    } else {
                        readAndReconcile()
                    }
                } else {
                    if (reconcileAllSnapshot(all, acceptedCoveredAtReadStart) &&
                        writeLedger.canDisplayAllTotal(all.total)
                    ) {
                        publish(mutableState.value.copy(allToday = all, isLoadingAll = false, hasAllError = false))
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

    private suspend fun fetchPair(): StatisticsPair =
        coroutineScope {
            val my = async { readSafely(statistics::findMyToday) }
            val all = async { readSafely(statistics::findAllToday) }
            StatisticsPair(my.await(), all.await())
        }

    private suspend fun <T> readSafely(read: suspend () -> PressReadResult<T>): PressReadResult<T> =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PressReadResult.Unavailable
        }

    private fun StatisticsPair.hasDifferentDates(): Boolean {
        val myDate = (my as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        val allDate = (all as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        return myDate != allDate
    }

    private fun publish(base: PressSessionState) {
        mutableState.value = writeLedger.applyTo(base)
    }

    /** Moves only acknowledged writes covered by this snapshot into the server baseline. */
    private fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedCoveredAtReadStart: Long,
    ): Boolean = writeLedger.reconcileAllSnapshot(snapshot, acceptedCoveredAtReadStart)

    private data class StatisticsPair(
        val my: PressReadResult<MyDailyPressSnapshot>,
        val all: PressReadResult<AllDailyPressSnapshot>,
    )

    private companion object {
        const val STATISTICS_TTL_MILLIS = 5_000L

        fun monotonicClock(): () -> PressMonotonicMillis {
            val origin = TimeSource.Monotonic.markNow()
            return { PressMonotonicMillis(origin.elapsedNow().inWholeMilliseconds) }
        }
    }
}
