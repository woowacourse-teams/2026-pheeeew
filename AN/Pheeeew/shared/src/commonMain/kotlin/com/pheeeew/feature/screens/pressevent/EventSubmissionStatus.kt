package com.pheeeew.feature.screens.pressevent

internal enum class EventSubmissionStatus {
    None,
    Submitting,
    Failed,
    Conflict,
    Rejected,
    Submitted,
    ;

    val canRetrySubmission: Boolean get() = this == Failed || this == Rejected
}
