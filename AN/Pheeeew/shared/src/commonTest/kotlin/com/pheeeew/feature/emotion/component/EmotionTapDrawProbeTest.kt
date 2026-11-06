package com.pheeeew.feature.emotion.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EmotionTapDrawProbeTest {
    @Test
    fun measuresAcceptedTapFromCallbackStartThroughItsFirstDraw() {
        var nowMicros = 100L
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { nowMicros }, output::add)

        val callback = invokeEmotionTapCallback(probe) {
            nowMicros = 112L
            true
        }
        nowMicros = 160L
        probe.particleAdded(callback.token, 41L)
        nowMicros = 187L
        probe.particleDrawn(41L)

        assertTrue(callback.accepted)
        assertEquals(1, output.size)
        assertTrue(output.single().contains("outcome=drawn"))
        assertTrue(output.single().contains("callbackUs=12"))
        assertTrue(output.single().contains("tapToDrawUs=87"))
        assertTrue(output.single().contains("drawAfterCallbackUs=75"))
    }

    @Test
    fun rejectedTapCompletesAtCallbackAndCannotBeCompletedByAnUnrelatedDraw() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 10L }, output::add)

        val callback = invokeEmotionTapCallback(probe) { false }
        probe.particleAdded(callback.token, 41L)
        probe.particleDrawn(41L)

        assertFalse(callback.accepted)
        assertEquals(1, output.size)
        assertTrue(output.single().contains("outcome=rejected"))
    }

    @Test
    fun multipleAcceptedTapsAreCorrelatedByTheirActualParticleIds() {
        var nowMicros = 0L
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { nowMicros }, output::add)
        val first = invokeEmotionTapCallback(probe) { true }
        nowMicros = 10L
        val second = invokeEmotionTapCallback(probe) { true }
        probe.particleAdded(first.token, 101L)
        probe.particleAdded(second.token, 202L)

        nowMicros = 20L
        probe.particleDrawn(202L)
        nowMicros = 30L
        probe.particleDrawn(101L)

        assertEquals(2, output.size)
        assertTrue(output[0].contains("sample=2 outcome=drawn"))
        assertTrue(output[1].contains("sample=1 outcome=drawn"))
    }

    @Test
    fun removedOldParticleCannotCompleteAStillPendingSample() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        val old = invokeEmotionTapCallback(probe) { true }
        probe.particleAdded(old.token, 7L)
        probe.particleRemoved(7L, EmotionTapDrawProbe.RemovalOutcome.Cancelled, "settled")

        val current = invokeEmotionTapCallback(probe) { true }
        probe.particleAdded(current.token, 8L)
        probe.particleDrawn(7L)

        assertEquals(1, output.size)
        assertTrue(output.single().contains("sample=1 outcome=cancelled accepted=true reason=settled"))

        probe.particleDrawn(8L)
        assertEquals(2, output.size)
        assertTrue(output.last().contains("sample=2 outcome=drawn"))
    }

    @Test
    fun stateEvictionIsReportedAndTheEvictedDrawStaysUnmatched() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        val state = TapFeedbackState<Int>(capacity = 1)
        val first = invokeEmotionTapCallback(probe) { true }
        val firstParticle = addParticle(state, now = 0.0)
        probe.particleAdded(first.token, firstParticle)

        val second = invokeEmotionTapCallback(probe) { true }
        val secondParticle =
            state.add(
                key = 1,
                reaction = reaction,
                flight = flight(100.0),
                now = 1.0,
                width = 10.0,
                height = 10.0,
                onEvicted = { id -> probe.particleRemoved(id, EmotionTapDrawProbe.RemovalOutcome.Evicted, "capacity_1") },
            )
        probe.particleAdded(second.token, secondParticle)
        probe.particleDrawn(firstParticle)

        assertTrue(output.single().contains("sample=1 outcome=evicted accepted=true reason=capacity_1"))
        probe.particleDrawn(secondParticle)
        assertTrue(output.last().contains("sample=2 outcome=drawn"))
    }

    @Test
    fun expirationBeforeDrawIsReportedAsMissing() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        val state = TapFeedbackState<Int>()
        val tap = invokeEmotionTapCallback(probe) { true }
        val particleId = addParticle(state, now = 0.0)
        probe.particleAdded(tap.token, particleId)

        state.advance(101.0) { id -> probe.particleRemoved(id, EmotionTapDrawProbe.RemovalOutcome.Missing, "expired_before_draw") }
        probe.particleDrawn(particleId)

        assertEquals(1, output.size)
        assertTrue(output.single().contains("outcome=missing accepted=true reason=expired_before_draw"))
    }

    @Test
    fun acceptedTapWithoutParticleAndFeedbackExceptionAreReportedAsMissing() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        val noParticle = invokeEmotionTapCallback(probe) { true }
        probe.feedbackCompleted(noParticle.token)
        val failedFeedback = invokeEmotionTapCallback(probe) { true }
        probe.feedbackThrew(failedFeedback.token)

        assertEquals(2, output.size)
        assertTrue(output[0].contains("outcome=missing accepted=true reason=particle_not_added"))
        assertTrue(output[1].contains("outcome=missing accepted=true reason=feedback_exception"))
    }

    @Test
    fun clearingPendingSamplesCancelsThemAndIgnoresLaterDraws() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        val tap = invokeEmotionTapCallback(probe) { true }
        probe.particleAdded(tap.token, 33L)

        probe.cancelAll("lifecycle_stopped")
        probe.particleDrawn(33L)

        assertEquals(1, output.size)
        assertTrue(output.single().contains("outcome=cancelled accepted=true reason=lifecycle_stopped"))
    }

    @Test
    fun pendingSampleLimitEmitsOneExplicitOverflowAndBoundsTracking() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 0L }, output::add)
        repeat(84) { index ->
            val tap = invokeEmotionTapCallback(probe) { true }
            probe.particleAdded(tap.token, index.toLong())
        }

        assertEquals(null, probe.begin())
        assertEquals(1, output.count { it.contains("outcome=overflow reason=pending_limit") })

        probe.cancelAll("test_cleanup")
        assertEquals(85, output.size)
    }

    @Test
    fun callbackExceptionIsRethrownUnchangedAndRecorded() {
        val output = mutableListOf<String>()
        val probe = EmotionTapDrawProbe(true, { 5L }, output::add)
        val expected = IllegalStateException("callback failed")

        val actual =
            try {
                invokeEmotionTapCallback(probe) { throw expected }
                error("callback exception was swallowed")
            } catch (failure: IllegalStateException) {
                failure
            }

        assertSame(expected, actual)
        assertTrue(output.single().contains("outcome=callback_exception"))
    }

    @Test
    fun probeSinkExceptionsCannotEscapeAndReleaseProbeDoesNotReadClock() {
        var clockReads = 0
        val disabled = EmotionTapDrawProbe(false, { clockReads++; error("disabled probe read clock") })
        assertEquals(null, disabled.begin())

        val brokenSink = EmotionTapDrawProbe(true, { 0L }) { error("diagnostic sink failed") }
        val tap = invokeEmotionTapCallback(brokenSink) { true }
        brokenSink.particleAdded(tap.token, 12L)
        brokenSink.particleDrawn(12L)

        assertEquals(0, clockReads)
        assertTrue(brokenSink.isEnabled)
    }

    private fun addParticle(
        state: TapFeedbackState<Int>,
        now: Double,
    ): Long =
        state.add(
            key = 1,
            reaction = reaction,
            flight = flight(100.0),
            now = now,
            width = 10.0,
            height = 10.0,
        )

    private fun flight(duration: Double) = TapFlight(0.0, 0.0, duration, List(33) { TapTransform() })

    private val reaction = TapReaction(TapReactionKind.Emoji, "🙂")
}
