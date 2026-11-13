package com.pheeeew.domain.press.settlement

import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch

/** A fixed batch and the personal snapshot used to determine whether it was applied. */
internal data class PressPendingWrite(
    val batch: PressBatch,
    val baseline: MyDailyPressSnapshot,
    val reconciliationMisses: Int = 0,
)
