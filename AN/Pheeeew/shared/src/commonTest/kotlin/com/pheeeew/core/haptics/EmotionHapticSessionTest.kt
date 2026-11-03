package com.pheeeew.core.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmotionHapticSessionTest {
    @Test
    fun repeatedInputsReuseOneBackendAndOnePlayer() {
        val fixture = Fixture()
        fixture.session.activate()
        fixture.session.activate()
        repeat(2000) { fixture.session.play() }
        assertEquals(1, fixture.backends.size)
        assertEquals(1, fixture.backends.single().playersCreated)
        assertEquals(2000, fixture.backends.single().plays)
    }

    @Test
    fun serverResetRecreatesPlayerWithoutCreatingAnotherEngine() {
        val fixture = Fixture()
        fixture.session.activate()
        val backend = fixture.backends.single()
        fixture.session.reset(backend)
        fixture.session.play()
        assertEquals(1, fixture.backends.size)
        assertEquals(2, backend.playersCreated)
        assertEquals(1, backend.plays)
    }

    @Test
    fun stoppedEngineRestartsButDoesNotReplayTheRecoveryTap() {
        val fixture = Fixture()
        fixture.session.activate()
        val backend = fixture.backends.single()
        fixture.session.stopped(backend)
        fixture.session.play()
        assertEquals(0, backend.plays)
        assertEquals(2, backend.starts)
        fixture.session.play()
        assertEquals(1, backend.plays)
        assertEquals(1, backend.playersCreated)
    }

    @Test
    fun exitClosesResourcesAndOldCallbacksCannotResetTheNextSession() {
        val fixture = Fixture()
        fixture.session.activate()
        val old = fixture.backends.single()
        fixture.session.deactivate()
        fixture.session.deactivate()
        fixture.session.reset(old)
        fixture.session.play()
        assertEquals(1, old.closes)
        assertEquals(0, old.plays)
        fixture.session.activate()
        val current = fixture.backends.last()
        fixture.session.reset(old)
        fixture.session.stopped(old)
        fixture.session.play()
        assertEquals(2, fixture.backends.size)
        assertEquals(1, current.playersCreated)
        assertEquals(1, current.plays)
    }

    @Test
    fun playbackFailureHasBoundedRecoveryAndNoDelayedReplay() {
        val fixture = Fixture()
        fixture.session.activate()
        val backend = fixture.backends.single()
        backend.playSucceeds = false
        fixture.session.play()
        repeat(2000) { fixture.session.play() }
        assertEquals(1, backend.starts)
        assertEquals(1, backend.plays)
        backend.playSucceeds = true
        fixture.now = 1000
        fixture.session.play()
        val recovered = fixture.backends.last()
        assertEquals(1, backend.closes)
        assertEquals(2, fixture.backends.size)
        assertEquals(1, recovered.playersCreated)
        assertEquals(1, backend.plays)
        fixture.session.play()
        assertEquals(1, recovered.plays)
    }

    @Test
    fun unsupportedHardwareNeverCreatesNativeResources() {
        val fixture = Fixture(supported = false)
        fixture.session.activate()
        repeat(100) { fixture.session.play() }
        assertTrue(fixture.backends.isEmpty())
    }

    @Test
    fun failedStartClosesPartialResourcesAndDoesNotRetryEveryInput() {
        var now = 0L
        val first = FakeBackend().apply { startSucceeds = false }
        val second = FakeBackend()
        var creations = 0
        val session = EmotionHapticSession(true, { now }) { if (++creations == 1) first else second }
        session.activate()
        repeat(100) { session.play() }
        assertEquals(1, creations)
        assertEquals(1, first.closes)
        now = 1000
        session.play()
        assertEquals(2, creations)
        assertEquals(0, second.plays)
    }

    @Test
    fun latestRequestIsBoundedAndPausedOwnersCannotProduceFeedback() {
        val gate = EmotionHapticRequestGate()
        val first = Any()
        val second = Any()
        gate.activate(first)
        gate.activate(second)
        assertTrue(gate.reserve(first, 0))
        repeat(2000) { assertFalse(gate.reserve(first, 0)) }
        gate.deactivate(first)
        assertTrue(gate.active)
        assertFalse(gate.consume(1))
        assertFalse(gate.reserve(first, 1))
        assertTrue(gate.reserve(second, 2))
        assertTrue(gate.consume(3))
        assertFalse(gate.consume(3))
    }

    @Test
    fun backgroundResetAndExpiryDiscardPendingFeedback() {
        val gate = EmotionHapticRequestGate()
        val owner = Any()
        gate.activate(owner)
        assertTrue(gate.reserve(owner, 0))
        gate.setEnabled(false)
        gate.setEnabled(true)
        assertFalse(gate.consume(10))
        assertTrue(gate.reserve(owner, 10))
        gate.invalidate()
        assertFalse(gate.consume(11))
        assertTrue(gate.reserve(owner, 20))
        assertFalse(gate.consume(101))
    }

    private class Fixture(
        supported: Boolean = true,
    ) {
        var now = 0L
        val backends = mutableListOf<FakeBackend>()
        val session = EmotionHapticSession(supported, { now }) { FakeBackend().also(backends::add) }
    }

    private class FakeBackend : EmotionHapticBackend {
        var starts = 0
        var plays = 0
        var closes = 0
        var playersCreated = 0
        var startSucceeds = true
        var playSucceeds = true
        private var hasPlayer = false

        override fun start(): Boolean {
            starts++
            if (!startSucceeds) return false
            if (!hasPlayer) {
                hasPlayer = true
                playersCreated++
            }
            return true
        }

        override fun play(): Boolean {
            plays++
            return playSucceeds
        }

        override fun invalidatePlayer() {
            hasPlayer = false
        }

        override fun close() {
            closes++
            hasPlayer = false
        }
    }
}
