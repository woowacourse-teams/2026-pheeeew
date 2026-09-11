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
        val requesting = reducer.reduce(BreathSessionState.Idle(), BreathSessionEvent.StartRequested)
        val sessionId = assertIs<BreathSessionState.RequestingPermission>(requesting.state).sessionId

        val listening =
            reducer.reduce(
                requesting.state,
                BreathSessionEvent.PermissionResult(sessionId, granted = true),
            )
        assertIs<BreathSessionState.RequestingLocationPermission>(listening.state)
        assertEquals(listOf(BreathSessionEffect.RequestLocationPermission(sessionId)), listening.effects)

        val locationGranted =
            reducer.reduce(
                listening.state,
                BreathSessionEvent.LocationPermissionResult(sessionId, granted = true),
            )
        assertIs<BreathSessionState.Listening>(locationGranted.state)
        assertEquals(listOf(BreathSessionEffect.StartInput(sessionId)), locationGranted.effects)

        val sampled =
            reducer.reduce(
                locationGranted.state,
                BreathSessionEvent.StrengthSample(sessionId, strength = 0.8f, elapsed = 220.milliseconds),
            )
        assertEquals(0.1f, assertIs<BreathSessionState.Listening>(sampled.state).growth)
    }

    @Test
    fun `callback from an old session is ignored`() {
        val requesting = reducer.reduce(BreathSessionState.Idle(), BreathSessionEvent.StartRequested)
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
        assertEquals(listOf(BreathSessionEffect.ShowNeedsMore(1L)), transition.effects)
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
        val requesting = reducer.reduce(BreathSessionState.Idle(), BreathSessionEvent.StartRequested)
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

    @Test
    fun `burst completion returns to idle while preserving session sequence`() {
        val bursting = BreathSessionState.Bursting(7L)
        val finished = reducer.reduce(bursting, BreathSessionEvent.BurstFinished(7L))

        assertEquals(BreathSessionState.Idle(7L), finished.state)
        val next = reducer.reduce(finished.state, BreathSessionEvent.StartRequested)
        assertEquals(8L, assertIs<BreathSessionState.RequestingPermission>(next.state).sessionId)
    }

    @Test
    fun `location denial returns idle without starting audio`() {
        val requestingMicrophone = reducer.reduce(BreathSessionState.Idle(), BreathSessionEvent.StartRequested)
        val microphoneGranted =
            reducer.reduce(
                requestingMicrophone.state,
                BreathSessionEvent.PermissionResult(1L, granted = true),
            )
        val locationDenied =
            reducer.reduce(
                microphoneGranted.state,
                BreathSessionEvent.LocationPermissionResult(1L, granted = false),
            )

        assertEquals(BreathSessionState.Idle(1L), locationDenied.state)
        assertTrue(locationDenied.effects.isEmpty())
    }
}
