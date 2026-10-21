package com.pheeeew.data.repository.press

import kotlin.test.Test
import kotlin.test.assertEquals

class PressFlushPolicyTest {
    private val policy = PressFlushPolicy(PressDelayMillis(500L))

    @Test
    fun `new input keeps first deadline across a read while an existing write can resume immediately`() {
        val deadline = policy.firstInputDeadline(monotonic(1_000L))
        assertEquals(monotonic(1_500L), deadline)
        val remainingWait = resumeDelay(deadline, hasPendingWrite = false, now = monotonic(1_200L))
        val pendingWriteWait = resumeDelay(deadline, hasPendingWrite = true, now = monotonic(1_200L))
        val expiredWait = resumeDelay(deadline, hasPendingWrite = false, now = monotonic(1_600L))
        assertEquals(delay(300L), remainingWait)
        assertEquals(PressDelayMillis.Zero, pendingWriteWait)
        assertEquals(PressDelayMillis.Zero, expiredWait)
    }

    @Test
    fun `a threshold skips batching delay and zero-delay configuration stays immediate`() {
        assertEquals(delay(500L), policy.delayAfterAcceptance(ready = false))
        assertEquals(PressDelayMillis.Zero, policy.delayAfterAcceptance(ready = true))
        val immediatePolicy = PressFlushPolicy(PressDelayMillis.Zero)
        assertEquals(PressDelayMillis.Zero, immediatePolicy.delayAfterAcceptance(ready = false))
    }

    @Test
    fun `expired or absent batch deadlines do not create another wait`() {
        val now = monotonic(1_200L)
        assertEquals(PressDelayMillis.Zero, PressFlushPolicy.remainingWait(null, now))
        assertEquals(PressDelayMillis.Zero, PressFlushPolicy.remainingWait(monotonic(1_000L), now))
        assertEquals(delay(200L), PressFlushPolicy.remainingWait(monotonic(1_400L), now))
    }

    @Test
    fun `deadline saturates near the clock maximum without turning into an immediate retry`() {
        val now = Long.MAX_VALUE - 20
        val monotonicNow = monotonic(now)
        val deadline = policy.firstInputDeadline(monotonicNow)
        assertEquals(monotonic(Long.MAX_VALUE), deadline)
        assertEquals(delay(20L), PressFlushPolicy.remainingWait(deadline, monotonicNow))
        assertEquals(
            monotonic(100L),
            PressFlushPolicy.deadline(monotonic(90L), delay(10L)),
        )
    }

    private fun monotonic(millis: Long): PressMonotonicMillis = PressMonotonicMillis(millis)

    private fun delay(millis: Long): PressDelayMillis = PressDelayMillis(millis)

    private fun resumeDelay(
        deadline: PressMonotonicMillis,
        hasPendingWrite: Boolean,
        now: PressMonotonicMillis,
    ): PressDelayMillis =
        policy.resumeDelay(
            batchDeadline = deadline,
            hasPendingWrite = hasPendingWrite,
            now = now,
        )
}
