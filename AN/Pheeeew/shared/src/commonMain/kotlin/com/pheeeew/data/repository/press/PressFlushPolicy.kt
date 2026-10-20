package com.pheeeew.data.repository.press

/** Timing decisions only. The repository owns deadlines, wakeups, jobs, and the first-input boundary. */
internal class PressFlushPolicy(
    private val flushDelayMillis: Long,
) {
    fun firstInputDeadline(now: Long): Long = deadline(now, flushDelayMillis)

    fun delayAfterAcceptance(ready: Boolean): Long = if (ready) 0L else flushDelayMillis

    /** Retrying an existing write resumes immediately; new input retains its original deadline. */
    fun resumeDelay(
        batchDeadline: Long?,
        hasPendingWrite: Boolean,
        now: Long,
    ): Long = if (hasPendingWrite) 0L else remainingWait(batchDeadline, now)

    companion object {
        fun remainingWait(
            deadline: Long?,
            now: Long,
        ): Long = ((deadline ?: now) - now).coerceAtLeast(0L)

        fun deadline(
            now: Long,
            wait: Long,
        ): Long = if (now > Long.MAX_VALUE - wait) Long.MAX_VALUE else now + wait
    }
}
