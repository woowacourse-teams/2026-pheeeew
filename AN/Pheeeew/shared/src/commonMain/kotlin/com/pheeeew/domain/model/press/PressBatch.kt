package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.press.time.PressEpochMillis
import kotlin.uuid.Uuid

data class PressBatch(
    val sequence: Long,
    val counts: Map<EmotionState, Int>,
    /** Local identity retained when this batch is retried; it is not sent as a server idempotency key. */
    val batchId: String = Uuid.random().toString(),
    val occurredAt: PressEpochMillis = PressEpochMillis(0L),
) {
    val totalCount: Int = counts.values.sum()
}
