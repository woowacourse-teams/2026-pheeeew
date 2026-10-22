package com.pheeeew.domain.model.press

import com.pheeeew.domain.press.time.PressDelayMillis

sealed interface PressSendResult {
    data class Accepted(
        val today: MyDailyPressTotals,
    ) : PressSendResult

    /** The server guarantees that this batch was not applied. */
    data object Rejected : PressSendResult

    /** Definitely not applied, so retrying is safe. */
    data object NotSent : PressSendResult

    /** Definitely not applied; the adapter supplies the server's minimum retry delay. */
    data class RetryAfter(
        val delayMillis: PressDelayMillis,
    ) : PressSendResult

    data object OutcomeUnknown : PressSendResult

    data object ContractPending : PressSendResult
}
