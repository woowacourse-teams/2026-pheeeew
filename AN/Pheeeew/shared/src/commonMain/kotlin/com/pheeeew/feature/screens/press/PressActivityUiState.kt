package com.pheeeew.feature.screens.press

/** Display state only. Activity counts and crossing results come from the state supplier. */
internal data class PressActivityUiState(
    val count: Long? = null,
    val status: PressActivityStatus = PressActivityStatus.Unknown,
    val notice: PressRaceNotice? = null,
) {
    init {
        require(count == null || count >= 0)
        require(status !in setOf(PressActivityStatus.Known, PressActivityStatus.Cached) || count != null)
    }

    val avatarCount: Int
        get() =
            if (status == PressActivityStatus.Known ||
                status == PressActivityStatus.Cached
            ) {
                requireNotNull(count).coerceAtMost(3).toInt()
            } else {
                0
            }

    val avatarOverflowCount: Long?
        get() =
            if (status == PressActivityStatus.Known ||
                status == PressActivityStatus.Cached
            ) {
                (requireNotNull(count) - 3).takeIf { it > 0 }
            } else {
                null
            }
}

internal enum class PressActivityStatus { Known, Cached, Loading, Unknown, Reconnecting }

/** A new displayId distinguishes a new occurrence even when the text is unchanged. */
internal data class PressRaceNotice(
    val displayId: Long,
    val enteredRanking: Boolean = false,
    val overtaken: Long = 0,
    val overtakenBy: Long = 0,
) {
    init {
        require(overtaken >= 0 && overtakenBy >= 0)
    }

    val isEmpty: Boolean get() = !enteredRanking && overtaken == 0L && overtakenBy == 0L
}
