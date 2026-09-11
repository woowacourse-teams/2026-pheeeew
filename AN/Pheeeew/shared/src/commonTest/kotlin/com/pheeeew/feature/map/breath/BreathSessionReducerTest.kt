package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInputError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BreathSessionReducerTest {
    private val reducer = BreathSessionReducer()

    @Test
    fun `start permission success and strength sample use a new session`() {
        val requesting = reducer.reduce(BreathSessionState.Idle, BreathSessionEvent.StartRequested)
        val sessionId = assertIs<BreathSessionState.RequestingPermission>(requesting.state).sessionId

        val listening =
            reducer.reduce(
                requesting.state,
                BreathSessionEvent.PermissionResult(sessionId, granted = true),
            )
        assertIs<BreathSessionState.Listening>(listening.state)
        assertEquals(listOf(BreathSessionEffect.StartInput(sessionId)), listening.effects)

        val sampled =
            reducer.reduce(
                listening.state,
                BreathSessionEvent.StrengthSample(sessionId, strength = 0.8f, elapsed = 220.milliseconds),
            )
        assertEquals(0.1f, assertIs<BreathSessionState.Listening>(sampled.state).growth)
    }

    @Test
    fun `callback from an old session is ignored`() {
        val requesting = reducer.reduce(BreathSessionState.Idle, BreathSessionEvent.StartRequested)
        val state = assertIs<BreathSessionState.RequestingPermission>(requesting.state)

        val ignored =
            reducer.reduce(
                state,
                BreathSessionEvent.PermissionResult(state.sessionId + 1L, granted = true),
            )
        assertEquals(state, ignored.state)
        assertTrue(ignored.effects.isEmpty())
    }

    @Test
    fun `insufficient release enters needs more without stopping input`() {
        val listening = BreathSessionState.Listening(1L, growth = 0.2f, strength = 0.7f, quietFor = 0.milliseconds)
        val transition =
            reducer.reduce(
                listening,
                BreathSessionEvent.ReleaseRequested(
                    sessionId = 1L,
                    upwardDistanceDp = 20f,
                    upwardVelocityDpPerSecond = -300f,
                ),
            )

        assertIs<BreathSessionState.NeedsMore>(transition.state)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun `successful release emits stop and burst exactly once`() {
        val listening = BreathSessionState.Listening(1L, growth = 0.4f, strength = 0.7f, quietFor = 0.milliseconds)
        val transition =
            reducer.reduce(
                listening,
                BreathSessionEvent.ReleaseRequested(1L, upwardDistanceDp = 40f, upwardVelocityDpPerSecond = -300f),
            )

        assertIs<BreathSessionState.Bursting>(transition.state)
        assertEquals(
            listOf(
                BreathSessionEffect.StopInput(1L),
                BreathSessionEffect.StartBurstAnimation(1L),
            ),
            transition.effects,
        )

        val repeated =
            reducer.reduce(
                transition.state,
                BreathSessionEvent.ReleaseRequested(1L, upwardDistanceDp = 40f, upwardVelocityDpPerSecond = -300f),
            )
        assertTrue(repeated.effects.isEmpty())
    }

    @Test
    fun `permission denial returns idle and exposes one error effect`() {
        val requesting = reducer.reduce(BreathSessionState.Idle, BreathSessionEvent.StartRequested)
        val state = assertIs<BreathSessionState.RequestingPermission>(requesting.state)
        val transition =
            reducer.reduce(
                state,
                BreathSessionEvent.PermissionResult(state.sessionId, granted = false),
            )

        assertIs<BreathSessionState.Idle>(transition.state)
        assertEquals(
            listOf(BreathSessionEffect.ShowError(BreathInputError.PermissionDenied)),
            transition.effects,
        )
    }

    @Test
    fun `lifecycle stop is idempotent`() {
        val listening = BreathSessionState.Listening(4L, growth = 0.5f, strength = 0.7f, quietFor = 0.milliseconds)
        val stopped = reducer.reduce(listening, BreathSessionEvent.LifecycleStopped)
        val repeated = reducer.reduce(stopped.state, BreathSessionEvent.LifecycleStopped)

        assertEquals(listOf(BreathSessionEffect.StopInput(4L)), stopped.effects)
        assertTrue(repeated.effects.isEmpty())
    }
}
