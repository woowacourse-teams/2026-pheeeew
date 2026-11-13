package com.pheeeew.domain.press.batching

import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis

/** Timing decisions only. The repository owns deadlines, wakeups, jobs, and the first-input boundary. */
internal class PressFlushPolicy(
    private val flushDelayMillis: PressDelayMillis,
) {
    fun firstInputDeadline(now: PressMonotonicMillis): PressMonotonicMillis = deadline(now, flushDelayMillis)

    fun delayAfterAcceptance(ready: Boolean): PressDelayMillis = if (ready) PressDelayMillis.Zero else flushDelayMillis

    /** Retrying an existing write resumes immediately; new input retains its original deadline. */
    fun resumeDelay(
        batchDeadline: PressMonotonicMillis?,
        hasPendingWrite: Boolean,
        now: PressMonotonicMillis,
    ): PressDelayMillis = if (hasPendingWrite) PressDelayMillis.Zero else remainingWait(batchDeadline, now)

    companion object {
        fun remainingWait(
            deadline: PressMonotonicMillis?,
            now: PressMonotonicMillis,
        ): PressDelayMillis = PressDelayMillis(((deadline ?: now).value - now.value).coerceAtLeast(0L))

        fun deadline(
            now: PressMonotonicMillis,
            wait: PressDelayMillis,
        ): PressMonotonicMillis =
            PressMonotonicMillis(
                if (now.value > Long.MAX_VALUE - wait.value) Long.MAX_VALUE else now.value + wait.value,
            )
    }
}
