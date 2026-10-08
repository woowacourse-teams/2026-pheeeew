package com.pheeeew.feature.screens.press

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.emotion.model.EmotionKind

internal data class PressBatch(
    val location: CurrentLocation,
    val counts: Map<EmotionState, Int>,
) {
    val totalCount: Int = counts.values.sum()
}

/** Bounded, single-owner counters; notification conflation can never drop accepted taps. */
internal class PressBatchAccumulator(
    private val maxOutstandingCount: Int,
) {
    private val pendingBatches = mutableListOf<MutablePressBatch>()
    private var queuedPressCount = 0

    val isNotEmpty: Boolean
        get() = pendingBatches.isNotEmpty()

    fun add(
        state: EmotionState,
        location: CurrentLocation,
    ): Boolean {
        if (queuedPressCount >= maxOutstandingCount) return false
        val batch =
            pendingBatches.lastOrNull()?.takeIf { it.location.hasSameCoordinates(location) }
                ?: MutablePressBatch(location).also(pendingBatches::add)
        batch.counts[state] = batch.counts.getValue(state) + 1
        batch.queuedPressCount++
        queuedPressCount++
        return true
    }

    fun isReadyToFlush(
        maxPerEmotion: Int = 30,
        maxTotal: Int = 100,
    ): Boolean {
        val oldest = pendingBatches.firstOrNull() ?: return false
        return pendingBatches.size > 1 ||
            oldest.queuedPressCount >= maxTotal ||
            oldest.counts.values.any { it >= maxPerEmotion }
    }

    fun take(
        maxPerEmotion: Int,
        maxTotal: Int,
        firstEmotionIndex: Int,
    ): PressBatch? {
        val oldest = pendingBatches.firstOrNull() ?: return null
        require(maxPerEmotion > 0 && maxTotal > 0)
        val orderedEmotions = EmotionState.entries
        val drained = linkedMapOf<EmotionState, Int>()
        var remaining = maxTotal
        for (offset in orderedEmotions.indices) {
            val state = orderedEmotions[(firstEmotionIndex + offset) % orderedEmotions.size]
            val amount = minOf(oldest.counts.getValue(state), maxPerEmotion, remaining)
            if (amount > 0) {
                drained[state] = amount
                oldest.counts[state] = oldest.counts.getValue(state) - amount
                oldest.queuedPressCount -= amount
                queuedPressCount -= amount
                remaining -= amount
            }
            if (remaining == 0) break
        }
        if (oldest.queuedPressCount == 0) pendingBatches.removeAt(0)
        return PressBatch(location = oldest.location, counts = drained)
    }

    fun add(
        emotion: EmotionKind,
        location: CurrentLocation,
    ): Boolean = add(emotion.toDomainState(), location)

    private fun EmotionKind.toDomainState(): EmotionState =
        when (this) {
            EmotionKind.Blocked -> EmotionState.FRUSTRATED
            EmotionKind.Annoyed -> EmotionState.IRRITATED
            EmotionKind.Tired -> EmotionState.EXHAUSTED
            EmotionKind.Defeated -> EmotionState.DISCOURAGED
            EmotionKind.Angry -> EmotionState.ANGRY
        }

    private fun CurrentLocation.hasSameCoordinates(other: CurrentLocation): Boolean =
        latitude == other.latitude && longitude == other.longitude

    private class MutablePressBatch(
        val location: CurrentLocation,
    ) {
        val counts = EmotionState.entries.associateWith { 0 }.toMutableMap()
        var queuedPressCount: Int = 0
    }
}
