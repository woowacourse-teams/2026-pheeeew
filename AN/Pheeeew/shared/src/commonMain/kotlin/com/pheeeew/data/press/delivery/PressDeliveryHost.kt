package com.pheeeew.data.press.delivery

import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.press.time.PressDelayMillis

/** 전송 객체가 세션의 읽기·상태 정산 규칙과 협력할 때 사용하는 좁은 경계다. */
internal interface PressDeliveryHost {
    fun canStartDelivery(): Boolean

    suspend fun prepareNextWrite(): PressDeliveryPreparation

    fun onSending(write: PressWriteState)

    suspend fun onSendResult(
        write: PressWriteState,
        result: PressSendResult,
    ): PressDeliveryDecision

    fun onSendCancelled(write: PressWriteState): Boolean

    fun isBatchReadyToFlush(): Boolean

    fun remainingBatchWait(): PressDelayMillis

    fun hasOutstandingWrites(): Boolean

    fun onQueueDrained()

    fun onDeliveryWorkerFinished(): PressDelayMillis?
}

internal sealed interface PressDeliveryPreparation {
    data class Ready(
        val write: PressWriteState,
    ) : PressDeliveryPreparation

    data class Retry(
        val retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero,
    ) : PressDeliveryPreparation

    data object Stop : PressDeliveryPreparation
}

internal sealed interface PressDeliveryDecision {
    data object Continue : PressDeliveryDecision

    data object Stop : PressDeliveryDecision

    data class Retry(
        val retryAfterMillis: PressDelayMillis = PressDelayMillis.Zero,
    ) : PressDeliveryDecision
}
