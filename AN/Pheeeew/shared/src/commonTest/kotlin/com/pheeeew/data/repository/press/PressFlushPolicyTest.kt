package com.pheeeew.data.repository.press

import kotlin.test.Test
import kotlin.test.assertEquals

class PressFlushPolicyTest {
    private val policy = PressFlushPolicy(500)

    @Test
    fun `new input keeps first deadline across a read while an existing write can resume immediately`() {
        val deadline = policy.firstInputDeadline(1000)
        assertEquals(1500, deadline)
        assertEquals(300, policy.resumeDelay(deadline, hasPendingWrite = false, now = 1200))
        assertEquals(0, policy.resumeDelay(deadline, hasPendingWrite = true, now = 1200))
        assertEquals(0, policy.resumeDelay(deadline, hasPendingWrite = false, now = 1600))
    }

    @Test
    fun `a threshold skips batching delay and zero-delay configuration stays immediate`() {
        assertEquals(500, policy.delayAfterAcceptance(ready = false))
        assertEquals(0, policy.delayAfterAcceptance(ready = true))
        assertEquals(0, PressFlushPolicy(0).delayAfterAcceptance(ready = false))
    }

    @Test
    fun `expired or absent batch deadlines do not create another wait`() {
        assertEquals(0, PressFlushPolicy.remainingWait(null, 1200))
        assertEquals(0, PressFlushPolicy.remainingWait(1000, 1200))
        assertEquals(200, PressFlushPolicy.remainingWait(1400, 1200))
    }

    @Test
    fun `deadline saturates near the clock maximum without turning into an immediate retry`() {
        val now = Long.MAX_VALUE - 20
        assertEquals(Long.MAX_VALUE, policy.firstInputDeadline(now))
        assertEquals(20, PressFlushPolicy.remainingWait(policy.firstInputDeadline(now), now))
        assertEquals(100, PressFlushPolicy.deadline(90, 10))
    }
}
