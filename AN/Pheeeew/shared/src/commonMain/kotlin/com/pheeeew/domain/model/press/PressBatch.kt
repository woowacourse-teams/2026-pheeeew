package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.uuid.Uuid

data class PressBatch(
    val sequence: Long,
    val counts: Map<EmotionState, Int>,
    val requestId: String = Uuid.random().toString(),
    val occurredAt: Long = 0L,
) {
    val totalCount: Int = counts.values.sum()
}
