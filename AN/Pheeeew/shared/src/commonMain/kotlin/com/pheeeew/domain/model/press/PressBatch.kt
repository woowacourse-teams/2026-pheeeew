package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.press.time.PressEpochMillis

data class PressBatch(
    val counts: Map<EmotionState, Int>,
    val occurredAt: PressEpochMillis = PressEpochMillis(0L),
) {
    val totalCount: Int = counts.values.sum()
}
