package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.domain.repository.press.PressSender
import com.pheeeew.domain.repository.press.PressStatisticsDataSource
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
import kotlin.time.TimeSource

/** Session-owned counters and one fixed write; all mutations run on the owner dispatcher. */
internal class PressRepositoryImpl(
    private val statistics: PressStatisticsDataSource,
    private val sender: PressSender,
    private val sessionScope: CoroutineScope,
    private val batchMaxPerEmotion: Int = 30,
    private val batchMaxTotal: Int = 100,
    private val flushDelayMillis: Long = 500L,
    private val retryPolicy: PressRetryPolicy = PressRetryPolicy(),
    connectivity: Flow<Boolean>? = null,
    private val nowMillis: () -> Long = monotonicClock(),
) : PressRepository {
    private val mutableState = MutableStateFlow(PressSessionState())
    override val state = mutableState.asStateFlow()
    private val batch = PressBatchAccumulator()
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private val optimisticCounts = EmotionState.entries.associateWith { 0L }.toMutableMap()
    private var outstanding = 0L
    private var optimisticAllPressCount = 0L
    private var acceptedSinceAllSnapshot = 0L
    private var pendingWrite: PendingWrite? = null
    private var nextSequence = 1L
    private var lastEmotionIndex = 0
    private var workerJob: Job? = null
    private var readJob: Job? = null
    private var retryJob: Job? = null
    private var statisticsRetryJob: Job? = null
    private var refreshAgain = false
    private var lastMySuccessfulRead = 0L
    private var lastAllSuccessfulRead = 0L
    private var hasMySuccessfulRead = false
    private var hasAllSuccessfulRead = false
    private var allStatsDirtyAfterWrite = false
    private var hasStarted = false
    private var readGeneration = 0L
    private var pausedNotice: PressSessionNotice? = null
    private var connected: Boolean? = null
    private var failures = 0
    private var statisticsFailures = 0
    private var retryNotBefore = 0L

    init {
        require(batchMaxPerEmotion > 0 && batchMaxTotal > 0)
        require(flushDelayMillis >= 0)
        connectivity?.let { updates ->
            sessionScope.launch {
                updates.distinctUntilChanged().collect { available ->
                    connected = available
                    if (available) {
                        onForeground()
                    } else if (outstanding > 0 && pausedNotice == null) {
                        publish(mutableState.value.copy(notice = PressSessionNotice.RetryRequired))
                    }
                }
            }
        }
    }

    override fun accept(emotion: EmotionState): PressAcceptance {
        val serverTotal = mutableState.value.myToday?.total ?: 0L
        val allServerTotal = mutableState.value.allToday?.total ?: 0L
        if (outstanding >= Long.MAX_VALUE - serverTotal ||
            optimisticAllPressCount >= Long.MAX_VALUE - allServerTotal ||
            !batch.add(emotion)
        ) {
            mutableState.update { it.copy(notice = PressSessionNotice.NumericLimit) }
            return PressAcceptance.NumericLimit
        }
        outstanding++
        optimisticAllPressCount++
        optimisticCounts[emotion] = optimisticCounts.getValue(emotion) + 1L
        if (pausedNotice == PressSessionNotice.Rejected) pausedNotice = null
        publish(
            mutableState.value.copy(
                notice =
                    when {
                        pendingWrite?.uncertain == true -> PressSessionNotice.OutcomeUnknown
                        pausedNotice != null -> pausedNotice
                        connected == false -> PressSessionNotice.RetryRequired
                        else -> null
                    },
            ),
        )
        val ready = batch.isReadyToFlush(batchMaxPerEmotion, batchMaxTotal)
        if (ready) flushRequests.trySend(Unit)
        scheduleSend(if (ready) 0L else flushDelayMillis)
        return PressAcceptance.Accepted
    }

    override fun onForeground() {
        if (!hasStarted && outstanding == 0L) return
        refreshToday()
        retryUnsent()
    }

    override fun refreshToday(force: Boolean) {
        hasStarted = true
        if (!force && hasFreshMySnapshot() && hasFreshAllSnapshot()) return
        if (workerJob?.isActive == true || readJob?.isActive == true) {
            refreshAgain = true
        } else {
            launchRead()
        }
    }

    override fun retryUnsent() {
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (pendingWrite?.uncertain == true && !canRetryUnknown()) {
            publish(mutableState.value.copy(notice = PressSessionNotice.OutcomeUnknown))
            return
        }
        if (outstanding > 0) scheduleSend(0L)
    }

    private fun canRetryUnknown(): Boolean {
        val write = pendingWrite ?: return false
        val window = sender.idempotencyWindowMillis ?: return false
        return window > 0 && nowMillis() - write.createdAtMillis < window
    }

    private fun scheduleSend(delayMillis: Long) {
        if (workerJob?.isActive == true || connected == false || outstanding == 0L) return
        if (pausedNotice == PressSessionNotice.WaitingForApiContract) return
        if (pendingWrite?.uncertain == true && !canRetryUnknown()) return
        lateinit var launchedWorker: Job
        launchedWorker =
            sessionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay((retryNotBefore - nowMillis()).coerceAtLeast(0L))
                    awaitFlush(delayMillis)
                    sendAvailable()
                } finally {
                    if (workerJob === launchedWorker) workerJob = null
                    if (refreshAgain && sessionScope.coroutineContext[Job]?.isActive != false) {
                        refreshAgain = false
                        launchRead()
                    } else if (outstanding > 0L && sessionScope.coroutineContext[Job]?.isActive != false) {
                        scheduleSend(0L)
                    }
                }
            }
        workerJob = launchedWorker
        launchedWorker.start()
    }

    private suspend fun sendAvailable() {
        while (outstanding > 0 && connected != false) {
            if (pendingWrite?.uncertain == true && !canRetryUnknown()) return
            readJob?.join()
            if (pendingWrite?.phase == WritePhase.AwaitingSnapshot || mutableState.value.myToday == null) {
                readAndReconcile()
                if (pendingWrite?.phase == WritePhase.AwaitingSnapshot || mutableState.value.myToday == null) {
                    scheduleRetry()
                    return
                }
            }
            if (connected == false) return
            val write =
                pendingWrite?.let { existing ->
                    if (existing.uncertain) {
                        existing
                    } else {
                        existing.copy(
                            baseline = checkNotNull(mutableState.value.myToday),
                        )
                    }
                } ?: newWrite() ?: return
            pendingWrite = write.copy(phase = WritePhase.InFlight)
            publish(mutableState.value.copy(isSending = true))
            val result =
                try {
                    sender.send(write.batch)
                } catch (cancelled: CancellationException) {
                    // Cancellation does not prove the server didn't apply the request.
                    pendingWrite = write.copy(phase = WritePhase.RetryPending, uncertain = true)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    if (canRetryUnknown()) scheduleRetry()
                    throw cancelled
                } catch (_: Exception) {
                    PressSendResult.OutcomeUnknown
                }
            when (result) {
                is PressSendResult.Accepted -> {
                    val current = checkNotNull(mutableState.value.myToday)
                    val updated = current.copy(counts = result.today.counts, total = result.today.total)
                    readGeneration++
                    removeOptimistic(write.batch)
                    acceptedSinceAllSnapshot += write.batch.totalCount
                    pendingWrite = null
                    failures = 0
                    pausedNotice = null
                    hasMySuccessfulRead = true
                    lastMySuccessfulRead = nowMillis()
                    allStatsDirtyAfterWrite = true
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
                    removeOptimistic(write.batch)
                    removeOptimisticAll(write.batch.totalCount.toLong())
                    pendingWrite = null
                    failures = 0
                    pausedNotice = PressSessionNotice.Rejected
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                }

                PressSendResult.NotSent, is PressSendResult.RetryAfter -> {
                    pendingWrite = write.copy(phase = WritePhase.RetryPending)
                    pausedNotice =
                        if (write.uncertain) PressSessionNotice.OutcomeUnknown else PressSessionNotice.RetryRequired
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    scheduleRetry((result as? PressSendResult.RetryAfter)?.delayMillis ?: 0L)
                    return
                }

                PressSendResult.ContractPending -> {
                    pendingWrite = write.copy(phase = WritePhase.RetryPending)
                    pausedNotice = PressSessionNotice.WaitingForApiContract
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    return
                }

                PressSendResult.OutcomeUnknown -> {
                    // The server may have committed this write. Reconcile with the aggregate API, but
                    // never replay it without a server-supported idempotency key.
                    pendingWrite = write.copy(phase = WritePhase.AwaitingSnapshot, uncertain = true)
                    pausedNotice = PressSessionNotice.OutcomeUnknown
                    publish(mutableState.value.copy(isSending = false, notice = pausedNotice))
                    readAndReconcile()
                    if (pendingWrite == null && allStatsDirtyAfterWrite) refreshAllAfterWrite()
                    if (pendingWrite?.uncertain == true && canRetryUnknown()) scheduleRetry()
                    return
                }
            }
            if (outstanding > 0) {
                val interval = if (batch.isReadyToFlush(batchMaxPerEmotion, batchMaxTotal)) 0L else flushDelayMillis
                delay(retryPolicy.minRequestIntervalMillis)
                awaitFlush(interval)
            }
        }
        if (outstanding == 0L && allStatsDirtyAfterWrite) refreshAllAfterWrite()
    }

    private suspend fun awaitFlush(delayMillis: Long) {
        if (delayMillis > 0 && !batch.isReadyToFlush(batchMaxPerEmotion, batchMaxTotal)) {
            withTimeoutOrNull(delayMillis) { flushRequests.receive() }
        }
        flushRequests.tryReceive()
    }

    private fun newWrite(): PendingWrite? {
        val next = batch.take(batchMaxPerEmotion, batchMaxTotal, lastEmotionIndex, nextSequence) ?: return null
        nextSequence = if (nextSequence == Long.MAX_VALUE) 1L else nextSequence + 1L
        lastEmotionIndex = (lastEmotionIndex + 1) % EmotionState.entries.size
        return PendingWrite(next, checkNotNull(mutableState.value.myToday), nowMillis())
    }

    private fun scheduleRetry(retryAfterMillis: Long = 0L) {
        failures = (failures + 1).coerceAtMost(64)
        val wait = retryPolicy.delayMillis(failures, retryAfterMillis)
        retryNotBefore = safeDeadline(nowMillis(), wait)
        retryJob?.cancel()
        retryJob =
            sessionScope.launch {
                delay(wait)
                workerJob?.join()
                scheduleSend(0L)
            }
    }

    private fun launchRead() {
        if (readJob?.isActive == true) {
            refreshAgain = true
            return
        }
        lateinit var launchedRead: Job
        launchedRead =
            sessionScope.launch(start = CoroutineStart.LAZY) {
                try {
                    readAndReconcile()
                } finally {
                    if (readJob === launchedRead) readJob = null
                    if (refreshAgain && sessionScope.coroutineContext[Job]?.isActive != false) {
                        refreshAgain = false
                        launchRead()
                    } else if (outstanding > 0) {
                        scheduleSend(0L)
                    }
                }
            }
        readJob = launchedRead
        launchedRead.start()
    }

    private suspend fun readAndReconcile() {
        val uncertainAtReadStart = pendingWrite?.uncertain == true
        val acceptedCoveredAtReadStart = if (uncertainAtReadStart) 0L else acceptedSinceAllSnapshot
        val generation = ++readGeneration
        mutableState.update {
            it.copy(isLoadingMy = true, isLoadingAll = true, hasMyError = false, hasAllError = false)
        }
        val first = fetchPair()
        val pair = if (first.hasDifferentDates()) fetchPair() else first
        if (generation < readGeneration) return
        val datesMatch = !pair.hasDifferentDates()
        val my = (pair.my as? PressReadResult.Loaded)?.value?.takeIf { datesMatch }
        val all = (pair.all as? PressReadResult.Loaded)?.value?.takeIf { datesMatch }
        val write = pendingWrite
        val confirmed = my != null && write?.phase == WritePhase.AwaitingSnapshot && write.isCoveredBy(my)
        if (confirmed) {
            removeOptimistic(checkNotNull(write).batch)
            acceptedSinceAllSnapshot += checkNotNull(write).batch.totalCount
            allStatsDirtyAfterWrite = true
            pendingWrite = null
            failures = 0
            retryJob?.cancel()
        } else if (write?.phase == WritePhase.AwaitingSnapshot) {
            // Keep the fixed batch and its baseline. A later replay is allowed only when the sender
            // advertises a server-enforced idempotency window.
            pendingWrite = write.copy(phase = WritePhase.RetryPending)
        }
        // Freeze the personal baseline until a pending write is covered; partial/stale reads must not double-count it.
        val readableMy =
            my?.takeIf {
                pendingWrite?.uncertain != true &&
                    pendingWrite?.phase != WritePhase.AwaitingSnapshot
            }
        val safeMy = readableMy?.takeIf { it.total <= Long.MAX_VALUE - outstanding }
        val safeAll = if (uncertainAtReadStart) null else all
        if (safeAll != null) reconcileAllSnapshot(safeAll, acceptedCoveredAtReadStart)
        val displayableAll =
            safeAll?.takeIf {
                it.total <= Long.MAX_VALUE - optimisticAllPressCount
            }
        publish(
            mutableState.value.copy(
                myToday = safeMy ?: mutableState.value.myToday,
                allToday = displayableAll ?: mutableState.value.allToday,
                isLoadingMy = false,
                isLoadingAll = false,
                hasMyError = my == null || (readableMy != null && safeMy == null),
                hasAllError = safeAll == null || safeAll.total > Long.MAX_VALUE - optimisticAllPressCount,
            ),
        )
        val readAt = nowMillis()
        if (safeMy != null) {
            lastMySuccessfulRead = readAt
            hasMySuccessfulRead = true
        }
        if (safeAll != null && safeAll.total <= Long.MAX_VALUE - optimisticAllPressCount) {
            lastAllSuccessfulRead = readAt
            hasAllSuccessfulRead = true
        }
        if (safeMy != null && all != null) {
            statisticsFailures = 0
            statisticsRetryJob?.cancel()
            statisticsRetryJob = null
        } else {
            scheduleStatisticsRetry()
        }
    }

    private suspend fun refreshAllAfterWrite() {
        allStatsDirtyAfterWrite = false
        val acceptedCoveredAtReadStart = acceptedSinceAllSnapshot
        publish(mutableState.value.copy(isLoadingAll = true, hasAllError = false))
        when (val result = readSafely(statistics::findAllToday)) {
            is PressReadResult.Loaded -> {
                val all = result.value
                val myDate = mutableState.value.myToday?.pressDate
                if (myDate != null && myDate != all.pressDate) {
                    readAndReconcile()
                } else {
                    if (reconcileAllSnapshot(all, acceptedCoveredAtReadStart) &&
                        all.total <= Long.MAX_VALUE - optimisticAllPressCount
                    ) {
                        publish(mutableState.value.copy(allToday = all, isLoadingAll = false, hasAllError = false))
                        lastAllSuccessfulRead = nowMillis()
                        hasAllSuccessfulRead = true
                        statisticsFailures = 0
                        statisticsRetryJob?.cancel()
                        statisticsRetryJob = null
                    } else {
                        publish(mutableState.value.copy(isLoadingAll = false, hasAllError = true))
                        hasAllSuccessfulRead = false
                        scheduleStatisticsRetry()
                    }
                }
            }

            PressReadResult.Unavailable -> {
                publish(mutableState.value.copy(isLoadingAll = false, hasAllError = true))
                hasAllSuccessfulRead = false
                scheduleStatisticsRetry()
            }
        }
    }

    private fun scheduleStatisticsRetry() {
        if (statisticsRetryJob?.isActive == true) return
        statisticsFailures = (statisticsFailures + 1).coerceAtMost(64)
        val wait = retryPolicy.delayMillis(statisticsFailures)
        statisticsRetryJob =
            sessionScope.launch {
                delay(wait)
                if (connected != false) launchRead()
            }
    }

    private fun hasFreshMySnapshot(): Boolean =
        hasMySuccessfulRead && nowMillis() - lastMySuccessfulRead < STATISTICS_TTL_MILLIS

    private fun hasFreshAllSnapshot(): Boolean =
        hasAllSuccessfulRead && nowMillis() - lastAllSuccessfulRead < STATISTICS_TTL_MILLIS

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

    private fun PendingWrite.isCoveredBy(snapshot: MyDailyPressSnapshot): Boolean {
        if (snapshot.pressDate != baseline.pressDate) return false
        if (baseline.total > Long.MAX_VALUE - batch.totalCount) return false
        if (snapshot.total < baseline.total + batch.totalCount) return false
        return batch.counts.all { (emotion, count) ->
            val before = baseline.counts.getValue(emotion)
            before <= Long.MAX_VALUE - count && snapshot.counts.getValue(emotion) >= before + count
        }
    }

    private fun publish(base: PressSessionState) {
        mutableState.value =
            base.copy(
                optimisticCounts = optimisticCounts.filterValues { it > 0L },
                optimisticAllPressCount = optimisticAllPressCount,
                pendingPressCount = outstanding,
                isOutcomeUnknown = pendingWrite?.uncertain == true,
            )
    }

    /** Moves only acknowledged writes covered by this snapshot into the server baseline. */
    private fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedCoveredAtReadStart: Long,
    ): Boolean {
        if (snapshot.total > Long.MAX_VALUE - (optimisticAllPressCount - acceptedCoveredAtReadStart)) return false
        check(acceptedSinceAllSnapshot >= acceptedCoveredAtReadStart)
        check(optimisticAllPressCount >= acceptedCoveredAtReadStart)
        acceptedSinceAllSnapshot -= acceptedCoveredAtReadStart
        optimisticAllPressCount -= acceptedCoveredAtReadStart
        return true
    }

    private fun removeOptimistic(accepted: PressBatch) {
        accepted.counts.forEach { (emotion, count) ->
            check(optimisticCounts.getValue(emotion) >= count)
            optimisticCounts[emotion] = optimisticCounts.getValue(emotion) - count
        }
        outstanding -= accepted.totalCount
    }

    private fun removeOptimisticAll(rejectedCount: Long) {
        check(optimisticAllPressCount >= rejectedCount)
        optimisticAllPressCount -= rejectedCount
    }

    private data class StatisticsPair(
        val my: PressReadResult<MyDailyPressSnapshot>,
        val all: PressReadResult<AllDailyPressSnapshot>,
    )

    private enum class WritePhase { InFlight, RetryPending, AwaitingSnapshot }

    private data class PendingWrite(
        val batch: PressBatch,
        val baseline: MyDailyPressSnapshot,
        val createdAtMillis: Long,
        val phase: WritePhase = WritePhase.InFlight,
        val uncertain: Boolean = false,
    )

    private companion object {
        const val STATISTICS_TTL_MILLIS = 5_000L

        fun monotonicClock(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }

        fun safeDeadline(
            now: Long,
            wait: Long,
        ): Long = if (now > Long.MAX_VALUE - wait) Long.MAX_VALUE else now + wait
    }
}
