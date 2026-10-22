package com.pheeeew.data.press.read

import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 세션과 같은 실행 문맥에서 조회 작업을 소유하고 결과를 동기적으로 전달한다. */
internal class PressReadCoordinator(
    statistics: PressStatisticsDataSource,
    private val scope: CoroutineScope,
    private val retryPolicy: PressRetryPolicy,
    private val aggregateRefreshMillis: PressDelayMillis,
    private val nowMillis: () -> PressMonotonicMillis,
    private val onEvent: (PressReadEvent) -> Unit,
) {
    private val reader = PressStatisticsReader(statistics)
    private val state = PressReadState()
    private var context = PressReadContext()
    private var connected: Boolean? = null
    private var readJob: Job? = null
    private var retryJob: Job? = null
    private var aggregateJob: Job? = null

    init {
        require(aggregateRefreshMillis.value > 0L)
    }

    /** 쓰기 상태의 최신 관찰값을 갱신하며 이미 시작한 조회의 문맥은 변경하지 않는다. */
    fun updateWriteContext(context: PressReadContext) {
        this.context = context
    }

    /** 예약된 통계 재시도와 전체 집계 갱신이 참고할 연결 상태를 기록한다. */
    fun onConnectivityChanged(connected: Boolean) {
        this.connected = connected
    }

    /** 유효 시간이 지난 통계를 조회하고 실행 중 들어온 요청은 다음 조회 하나로 합친다. */
    fun refreshToday(
        force: Boolean = false,
        writeWorkerActive: Boolean = false,
    ) {
        val now = nowMillis()
        if (!force && state.hasFreshMySnapshot(now, SNAPSHOT_TTL) &&
            state.hasFreshAllSnapshot(now, SNAPSHOT_TTL)
        ) {
            return
        }
        if (writeWorkerActive || readJob?.isActive == true) {
            state.requestAnotherRead()
        } else {
            launchRead()
        }
    }

    /** 전송 작업이 끝난 뒤 보류했던 조회가 있으면 실행한다. */
    fun resumeRequestedRead(): Boolean {
        if (!state.consumeAnotherRead(scope.coroutineContext[Job]?.isActive != false)) return false
        launchRead()
        return true
    }

    /** 현재 진행 중인 개인·전체 조회가 끝날 때까지 전송을 기다리게 한다. */
    suspend fun awaitCurrentRead() {
        readJob?.join()
    }

    /** 개인 쓰기 성공으로 이전 조회를 무효화하고 전체 집계 갱신 필요 상태를 기록한다. */
    fun personalWriteSucceeded() {
        state.personalWriteSucceeded(nowMillis())
    }

    /** 처리 불명 쓰기가 확인된 뒤 전체 집계를 다시 읽도록 표시한다. */
    fun markAggregateRefreshPending() {
        state.markAggregateRefreshPending()
    }

    /** 시작 시점의 쓰기 문맥과 날짜가 일치하는 개인·전체 조회 결과를 전달한다. */
    suspend fun readNow() {
        val startedContext = context
        val generation = state.beginRead()
        onEvent(PressReadEvent.PairStarted)
        val initial = reader.readPair()
        val consistent = if (initial.hasDifferentDates()) reader.readPair() else initial
        if (state.isStale(generation)) return
        val datesMatch = !consistent.hasDifferentDates()
        onEvent(
            PressReadEvent.PairCompleted(
                personal = (consistent.personal as? PressReadResult.Loaded)?.value?.takeIf { datesMatch },
                aggregate = (consistent.aggregate as? PressReadResult.Loaded)?.value?.takeIf { datesMatch },
                context = startedContext,
            ),
        )
    }

    /** 세션에 적용된 스냅샷만 최신으로 기록하고 실패한 통계는 재시도한다. */
    fun completePairedRead(
        personalApplied: Boolean,
        aggregateApplied: Boolean,
    ) {
        val completedAt = nowMillis()
        if (personalApplied) state.recordMySnapshotSuccess(completedAt)
        if (aggregateApplied) state.recordAllSnapshotSuccess(completedAt)
        if (personalApplied && aggregateApplied) {
            resetRetries()
        } else {
            scheduleRetry()
        }
    }

    /** 필요한 전체 집계 조회를 쓰기 작업 안에서 즉시 수행한다. */
    suspend fun refreshAggregateIfPending() {
        if (state.hasAggregateRefreshPending()) readAggregate(detached = false)
    }

    /** 전송이 끝난 뒤 전체 집계 갱신을 하나의 지연 작업으로 합친다. */
    fun scheduleAggregateRefresh() {
        if (aggregateJob?.isActive == true || !state.hasAggregateRefreshPending()) return
        aggregateJob =
            scope.launch {
                while (state.hasAggregateRefreshPending() && connected != false && !context.writeOutcomeUnknown) {
                    delay(aggregateRefreshMillis.value)
                    if (!context.writeInFlight) readAggregate(detached = true)
                }
            }
    }

    /** 전체 집계 적용 결과에 따라 최신성 기록이나 조회 재시도 예약을 갱신한다. */
    fun completeAggregateRead(applied: Boolean) {
        if (applied) {
            state.recordAllSnapshotSuccess(nowMillis())
            resetRetries()
        } else {
            state.invalidateAllSnapshot()
            scheduleRetry()
        }
    }

    /** 통계 조회를 직렬 실행하고 마지막 조회가 끝나면 세션에 전송 재개 기회를 알린다. */
    private fun launchRead() {
        if (readJob?.isActive == true) {
            state.requestAnotherRead()
            return
        }
        lateinit var launchedRead: Job
        launchedRead =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    readNow()
                } finally {
                    if (readJob === launchedRead) readJob = null
                    if (!resumeRequestedRead()) onEvent(PressReadEvent.PairIdle)
                }
            }
        readJob = launchedRead
        launchedRead.start()
    }

    /** 분리 실행한 전체 조회의 오래된 응답을 폐기하고 날짜가 달라지면 쌍 조회로 전환한다. */
    private suspend fun readAggregate(detached: Boolean) {
        state.clearAggregateRefreshPending()
        val startedContext = context
        val generation = state.currentGeneration()
        onEvent(PressReadEvent.AggregateStarted)
        when (val result = reader.readAggregate()) {
            is PressReadResult.Loaded -> {
                if (detached &&
                    !PressAggregateReadPolicy.canApply(
                        generation,
                        state.currentGeneration(),
                        context.writeInFlight,
                        context.writeOutcomeUnknown,
                    )
                ) {
                    state.markAggregateRefreshPending()
                    onEvent(PressReadEvent.AggregateDiscarded)
                    return
                }
                val personalDate = context.personalPressDate
                if (personalDate != null && personalDate != result.value.pressDate) {
                    if (detached) {
                        onEvent(PressReadEvent.PairRefreshRequired)
                    } else {
                        readNow()
                    }
                } else {
                    onEvent(PressReadEvent.AggregateCompleted(result.value, startedContext))
                }
            }

            PressReadResult.Unavailable -> {
                onEvent(PressReadEvent.AggregateCompleted(null, startedContext))
            }
        }
    }

    /** 실패한 통계 조회를 정책의 대기 시간 뒤에 다시 실행한다. */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val wait = state.nextStatisticsRetryDelay(retryPolicy)
        retryJob =
            scope.launch {
                delay(wait.value)
                retryJob = null
                if (connected != false) launchRead()
            }
    }

    /** 조회 실패 횟수와 예약된 재시도를 함께 초기화한다. */
    private fun resetRetries() {
        state.resetStatisticsFailures()
        retryJob?.cancel()
        retryJob = null
    }

    private companion object {
        val SNAPSHOT_TTL = PressDelayMillis(5_000L)
    }
}
