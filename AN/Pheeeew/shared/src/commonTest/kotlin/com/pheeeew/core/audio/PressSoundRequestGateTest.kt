package com.pheeeew.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressSoundRequestGateTest {
    @Test
    fun simultaneousTapsArePreservedUpToBoundAndStopInvalidatesThem() {
        val gate = PressSoundRequestGate(maxPending = 3)
        assertTrue(gate.canLoad())
        assertNull(gate.reserve())
        gate.resume()
        val first = assertNotNull(gate.reserve())
        val second = assertNotNull(gate.reserve())
        val third = assertNotNull(gate.reserve())
        repeat(100) { assertNull(gate.reserve()) }
        assertTrue(gate.consume(first))
        assertTrue(gate.consume(second))
        assertTrue(gate.consume(third))
        val pending = assertNotNull(gate.reserve())
        gate.stop()
        assertFalse(gate.consume(pending))
        assertNull(gate.reserve())
    }

    @Test
    fun resumeCannotReplayAnOldSoundOrLetItsCallbackClearANewOne() {
        val gate = PressSoundRequestGate(maxPending = 3)
        gate.resume()
        val old = assertNotNull(gate.reserve())
        gate.stop()
        val resumed = gate.resume()
        val current = assertNotNull(gate.reserve())
        assertNotNull(gate.reserve())
        assertNotNull(gate.reserve())
        assertEquals(resumed, current)
        assertFalse(gate.consume(old))
        assertNull(gate.reserve())
        assertTrue(gate.consume(current))
        gate.release()
        gate.resume()
        assertNull(gate.reserve())
        assertFalse(gate.canLoad())
    }
}
