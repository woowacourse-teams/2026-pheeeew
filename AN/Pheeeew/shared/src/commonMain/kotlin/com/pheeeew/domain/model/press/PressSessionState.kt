package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.uuid.Uuid

/** Immutable state retained by the app-session press repository. */
data class PressSessionState(
    val myToday: MyDailyPressSnapshot? = null,
    val allToday: AllDailyPressSnapshot? = null,
    val isLoadingMy: Boolean = true,
    val isLoadingAll: Boolean = true,
    val hasMyError: Boolean = false,
    val hasAllError: Boolean = false,
    val optimisticCounts: Map<EmotionState, Long> = emptyMap(),
    /** Local presses not yet represented by the latest successful all-user total. */
    val optimisticAllPressCount: Long = 0L,
    val pendingPressCount: Long = 0L,
    val isSending: Boolean = false,
    val isOutcomeUnknown: Boolean = false,
    val notice: PressSessionNotice? = null,
)

enum class PressSessionNotice {
    NumericLimit,
    Rejected,
    RetryRequired,
    OutcomeUnknown,
    WaitingForApiContract,
}

enum class PressAcceptance {
    Accepted,
    NumericLimit,
}

data class PressBatch(
    val sequence: Long,
    val counts: Map<EmotionState, Int>,
    val requestId: String = Uuid.random().toString(),
) {
    val totalCount: Int = counts.values.sum()
}

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
        val delayMillis: Long,
    ) : PressSendResult {
        init {
            require(delayMillis >= 0L)
        }
    }

    data object OutcomeUnknown : PressSendResult

    data object ContractPending : PressSendResult
}
