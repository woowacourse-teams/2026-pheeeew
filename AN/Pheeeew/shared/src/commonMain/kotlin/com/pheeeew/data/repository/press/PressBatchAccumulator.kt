package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch

/** Five counters, regardless of tap volume. Only bounded request counts are narrowed to Int. */
internal class PressBatchAccumulator {
    private val counts = EmotionState.entries.associateWith { 0L }.toMutableMap()
    var size: Long = 0L
        private set

    val isNotEmpty: Boolean
        get() = size > 0L

    fun isReadyToFlush(
        maxPerEmotion: Int,
        maxTotal: Int,
    ): Boolean = size >= maxTotal.toLong() || counts.values.any { it >= maxPerEmotion.toLong() }

    fun add(
        emotion: EmotionState,
        count: Long = 1L,
    ): Boolean {
        require(count > 0L)
        if (size > Long.MAX_VALUE - count) return false
        counts[emotion] = counts.getValue(emotion) + count
        size += count
        return true
    }

    fun take(
        maxPerEmotion: Int,
        maxTotal: Int,
        firstEmotionIndex: Int,
        sequence: Long,
    ): PressBatch? {
        require(maxPerEmotion > 0 && maxTotal > 0)
        if (!isNotEmpty) return null
        val drained = linkedMapOf<EmotionState, Int>()
        var remaining = maxTotal
        for (offset in EmotionState.entries.indices) {
            val emotion = EmotionState.entries[(firstEmotionIndex + offset) % EmotionState.entries.size]
            val amount = minOf(counts.getValue(emotion), maxPerEmotion.toLong(), remaining.toLong()).toInt()
            if (amount > 0) {
                drained[emotion] = amount
                counts[emotion] = counts.getValue(emotion) - amount
                size -= amount
                remaining -= amount
            }
            if (remaining == 0) break
        }
        return PressBatch(sequence, drained.toMap())
    }
}
