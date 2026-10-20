package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch

/** Internal write lifecycle; owned and changed only by the session repository. */
internal enum class PressWritePhase { InFlight, RetryPending, AwaitingSnapshot }

internal data class PressWriteState(
    val batch: PressBatch,
    val baseline: MyDailyPressSnapshot,
    val createdAtMillis: Long,
    val phase: PressWritePhase = PressWritePhase.InFlight,
    val uncertain: Boolean = false,
    val reconciliationMisses: Int = 0,
)
