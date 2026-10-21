package com.pheeeew.data.repository.press

import kotlin.jvm.JvmInline

/** Monotonic time used for elapsed-time calculations; it is not a calendar timestamp. */
@JvmInline
internal value class PressMonotonicMillis(
    val value: Long,
)

/** Unix epoch time used for input dates and activity reporting. */
@JvmInline
internal value class PressEpochMillis(
    val value: Long,
)

/** Non-negative elapsed time or wait passed to coroutine timers. */
@JvmInline
internal value class PressDelayMillis(
    val value: Long,
) {
    init {
        require(value >= 0L)
    }

    companion object {
        val Zero = PressDelayMillis(0L)
    }
}
