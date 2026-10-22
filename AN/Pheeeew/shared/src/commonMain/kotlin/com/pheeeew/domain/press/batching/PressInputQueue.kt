package com.pheeeew.domain.press.batching

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressEpochMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis

/** Owns accepted input until it is moved into a fixed write batch. */
internal class PressInputQueue(
    private val maxPerEmotion: Int,
    private val maxTotal: Int,
    flushDelayMillis: PressDelayMillis,
) {
    private val accumulator = PressBatchAccumulator()
    private val flushPolicy = PressFlushPolicy(flushDelayMillis)
    private var flushDeadline: PressMonotonicMillis? = null
    private var nextSequence = 1L
    private var firstEmotionIndex = 0

    init {
        require(maxPerEmotion > 0 && maxTotal > 0)
    }

    fun accept(
        emotion: EmotionState,
        occurredAt: PressEpochMillis,
        now: PressMonotonicMillis,
    ): PressQueueAcceptance {
        val wasEmpty = !accumulator.isNotEmpty
        if (!accumulator.add(emotion, occurredAt = occurredAt)) return PressQueueAcceptance.NumericLimit
        if (wasEmpty) flushDeadline = flushPolicy.firstInputDeadline(now)
        val isThresholdReached = isReadyToFlush()
        return PressQueueAcceptance.Accepted(
            isThresholdReached = isThresholdReached,
            delayMillis = flushPolicy.delayAfterAcceptance(isThresholdReached),
        )
    }

    fun isReadyToFlush(): Boolean = accumulator.isReadyToFlush(maxPerEmotion, maxTotal)

    fun remainingWait(now: PressMonotonicMillis): PressDelayMillis = PressFlushPolicy.remainingWait(flushDeadline, now)

    fun resumeDelay(
        hasPendingWrite: Boolean,
        now: PressMonotonicMillis,
    ): PressDelayMillis = flushPolicy.resumeDelay(flushDeadline, hasPendingWrite, now)

    fun takeBatch(): PressBatch? {
        val next = accumulator.take(maxPerEmotion, maxTotal, firstEmotionIndex, nextSequence) ?: return null
        if (!accumulator.isNotEmpty) flushDeadline = null
        nextSequence = if (nextSequence == Long.MAX_VALUE) 1L else nextSequence + 1L
        firstEmotionIndex = (firstEmotionIndex + 1) % EmotionState.entries.size
        return next
    }
}

internal sealed interface PressQueueAcceptance {
    data object NumericLimit : PressQueueAcceptance

    data class Accepted(
        val isThresholdReached: Boolean,
        val delayMillis: PressDelayMillis,
    ) : PressQueueAcceptance
}
