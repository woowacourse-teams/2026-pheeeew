package com.pheeeew.data.repository.press

import com.pheeeew.core.monitoring.activityDate
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch

/** Five counters per input day, not per tap. A request never mixes KST dates. */
internal class PressBatchAccumulator {
    private class Day(
        val occurredAt: Long,
    ) {
        val counts = EmotionState.entries.associateWith { 0L }.toMutableMap()
    }

    private val days = linkedMapOf<String, Day>()
    var size: Long = 0L
        private set

    val isNotEmpty: Boolean get() = size > 0L

    fun isReadyToFlush(
        maxPerEmotion: Int,
        maxTotal: Int,
    ): Boolean =
        size >= maxTotal.toLong() || days.values.any { day -> day.counts.values.any { it >= maxPerEmotion.toLong() } }

    fun add(
        emotion: EmotionState,
        count: Long = 1L,
        occurredAt: Long = 0L,
    ): Boolean {
        require(count > 0L)
        if (size > Long.MAX_VALUE - count) return false
        val day = days.getOrPut(activityDate(occurredAt)) { Day(occurredAt) }
        day.counts[emotion] = day.counts.getValue(emotion) + count
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
        val entry = days.entries.firstOrNull() ?: return null
        val day = entry.value
        val drained = linkedMapOf<EmotionState, Int>()
        var remaining = maxTotal
        for (offset in EmotionState.entries.indices) {
            val emotion = EmotionState.entries[(firstEmotionIndex + offset) % EmotionState.entries.size]
            val amount = minOf(day.counts.getValue(emotion), maxPerEmotion.toLong(), remaining.toLong()).toInt()
            if (amount > 0) {
                drained[emotion] = amount
                day.counts[emotion] = day.counts.getValue(emotion) - amount
                size -= amount
                remaining -= amount
            }
            if (remaining == 0) break
        }
        if (day.counts.values.all { it == 0L }) days.remove(entry.key)
        return PressBatch(sequence, drained.toMap(), occurredAt = day.occurredAt)
    }
}
