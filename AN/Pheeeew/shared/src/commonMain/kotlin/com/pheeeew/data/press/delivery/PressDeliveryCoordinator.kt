package com.pheeeew.data.press.delivery

import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.press.batching.PressFlushPolicy
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 직렬 전송 워커, 묶음 신호와 전송 재시도 예약을 소유한다. */
internal class PressDeliveryCoordinator(
    private val sender: PressSender,
    private val scope: CoroutineScope,
    private val retryPolicy: PressRetryPolicy,
    private val nowMillis: () -> PressMonotonicMillis,
    private val host: PressDeliveryHost,
) {
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private var workerJob: Job? = null
    private var retryJob: Job? = null
    private var consecutiveFailures = 0
    private var retryDeadline = PressMonotonicMillis(0L)

    /** 임계값을 채운 입력이 현재 묶음 대기 중인 워커를 깨우게 한다. */
    fun requestFlush() {
        flushRequests.trySend(Unit)
    }

    /** 조건을 만족할 때만 단일 직렬 전송 워커를 시작한다. */
    fun scheduleSend(delayMillis: PressDelayMillis) {
        if (workerJob?.isActive == true || !host.canStartDelivery()) return
        lateinit var launchedWorker: Job
        launchedWorker =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    delay(PressFlushPolicy.remainingWait(retryDeadline, nowMillis()).value)
                    awaitFlush(delayMillis)
                    sendAvailable()
                } finally {
                    if (workerJob === launchedWorker) workerJob = null
                    if (scope.coroutineContext[Job]?.isActive != false) {
                        host.onDeliveryWorkerFinished()?.let(::scheduleSend)
                    }
                }
            }
        workerJob = launchedWorker
        launchedWorker.start()
    }

    /** 진행 중인 통계 재시도와 전송 재시도 횟수를 초기화한다. */
    fun resetRetryState() {
        resetFailureCount()
        retryJob?.cancel()
        retryJob = null
    }

    /** 성공·확정 거절 뒤 연속 실패 횟수만 초기화한다. */
    private fun resetFailureCount() {
        consecutiveFailures = 0
    }

    /** 전송 워커의 실행 여부를 조회 담당 객체에 전달한다. */
    fun isWorkerActive(): Boolean = workerJob?.isActive == true

    /** 준비된 쓰기를 순서대로 보내고 결과별 재시도 정책을 실행한다. */
    private suspend fun sendAvailable() {
        while (host.canStartDelivery()) {
            when (val preparation = host.prepareNextWrite()) {
                PressDeliveryPreparation.Stop -> {
                    return
                }

                is PressDeliveryPreparation.Retry -> {
                    scheduleRetry(preparation.retryAfterMillis)
                    return
                }

                is PressDeliveryPreparation.Ready -> {
                    if (!send(preparation.write)) return
                }
            }
        }
        if (!host.hasOutstandingWrites()) host.onQueueDrained()
    }

    /** API 응답을 세션에 정산하도록 전달하고 결정에 따라 이어서 보내거나 멈춘다. */
    private suspend fun send(write: PressWriteState): Boolean {
        host.onSending(write)
        val result =
            try {
                sender.send(write.pending.batch)
            } catch (cancelled: CancellationException) {
                if (host.onSendCancelled(write)) scheduleRetry()
                throw cancelled
            } catch (_: Exception) {
                PressSendResult.OutcomeUnknown
            }
        return when (val decision = host.onSendResult(write, result)) {
            PressDeliveryDecision.Continue -> {
                resetFailureCount()
                awaitNextWriteIfNeeded()
                true
            }

            PressDeliveryDecision.Stop -> {
                false
            }

            is PressDeliveryDecision.Retry -> {
                scheduleRetry(decision.retryAfterMillis)
                false
            }
        }
    }

    /** 다음 직렬 요청의 간격과 남은 묶음 대기를 적용한다. */
    private suspend fun awaitNextWriteIfNeeded() {
        if (!host.hasOutstandingWrites()) return
        delay(retryPolicy.minRequestIntervalMillis.value)
        awaitFlush(host.remainingBatchWait())
    }

    /** 묶음 대기 또는 임계값 신호가 올 때까지 현재 워커를 보류한다. */
    private suspend fun awaitFlush(delayMillis: PressDelayMillis) {
        if (delayMillis.value > 0L && !host.isBatchReadyToFlush()) {
            withTimeoutOrNull(delayMillis.value) { flushRequests.receive() }
        }
        flushRequests.tryReceive()
    }

    /** 실패 횟수와 Retry-After를 반영해 한 번의 재시도 작업을 예약한다. */
    private fun scheduleRetry(retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero) {
        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(64)
        val wait = retryPolicy.delayMillis(consecutiveFailures, retryAfterMillis)
        retryDeadline = PressFlushPolicy.deadline(nowMillis(), wait)
        retryJob?.cancel()
        retryJob =
            scope.launch {
                delay(wait.value)
                workerJob?.join()
                scheduleSend(PressDelayMillis.Zero)
            }
    }
}
