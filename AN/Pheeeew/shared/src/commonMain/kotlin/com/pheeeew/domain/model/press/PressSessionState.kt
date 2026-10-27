package com.pheeeew.domain.model.press

import com.pheeeew.domain.model.emotion.EmotionState

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
    val isInputEnabled: Boolean = true,
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
    Unavailable,
    Accepted,
    NumericLimit,
}
