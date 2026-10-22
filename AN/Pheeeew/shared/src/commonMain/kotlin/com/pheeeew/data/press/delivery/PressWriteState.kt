package com.pheeeew.data.press.delivery

import com.pheeeew.domain.press.settlement.PressPendingWrite
import com.pheeeew.domain.press.time.PressMonotonicMillis

/** Transport lifecycle and retry age, separate from the domain's settlement evidence. */
internal enum class PressWritePhase { InFlight, RetryPending, AwaitingSnapshot }

internal data class PressWriteState(
    val pending: PressPendingWrite,
    val createdAtMonotonicMillis: PressMonotonicMillis,
    val phase: PressWritePhase = PressWritePhase.InFlight,
    val uncertain: Boolean = false,
)
