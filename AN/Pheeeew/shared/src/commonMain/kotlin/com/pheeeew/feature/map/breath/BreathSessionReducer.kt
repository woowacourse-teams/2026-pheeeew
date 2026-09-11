package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInputError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

data class BreathSessionTransition(
    val state: BreathSessionState,
    val effects: List<BreathSessionEffect> = emptyList(),
)

/** 시간과 입력 이벤트만 받아 결정론적으로 한숨 상태를 전이합니다. */
class BreathSessionReducer(
    private val config: BreathInteractionConfig = BreathInteractionConfig(),
) {
    fun reduce(
        state: BreathSessionState,
        event: BreathSessionEvent,
    ): BreathSessionTransition =
        when (event) {
            BreathSessionEvent.StartRequested -> start(state)
            is BreathSessionEvent.PermissionResult -> permissionResult(state, event)
            is BreathSessionEvent.LocationPermissionResult -> locationPermissionResult(state, event)
            is BreathSessionEvent.StrengthSample -> strengthSample(state, event)
            is BreathSessionEvent.ReleaseRequested -> release(state, event)
            BreathSessionEvent.CancelRequested -> stop(state)
            BreathSessionEvent.LifecycleStopped -> stop(state)
            is BreathSessionEvent.BurstFinished -> burstFinished(state, event)
            is BreathSessionEvent.InputFailed -> inputFailed(state, event)
        }

    private fun start(state: BreathSessionState): BreathSessionTransition {
        if (state !is BreathSessionState.Idle) return BreathSessionTransition(state)
        val sessionId = state.sessionId + 1L
        return BreathSessionTransition(
            state = BreathSessionState.RequestingPermission(sessionId),
            effects = listOf(BreathSessionEffect.RequestMicrophonePermission(sessionId)),
        )
    }

    private fun permissionResult(
        state: BreathSessionState,
        event: BreathSessionEvent.PermissionResult,
    ): BreathSessionTransition {
        if (state !is BreathSessionState.RequestingPermission || state.sessionId != event.sessionId) {
            return BreathSessionTransition(state)
        }
        if (!event.granted) {
            return BreathSessionTransition(
                state = BreathSessionState.Idle(state.sessionId),
                effects = listOf(BreathSessionEffect.ShowError(BreathInputError.PermissionDenied)),
            )
        }
        return BreathSessionTransition(
            state = BreathSessionState.RequestingLocationPermission(state.sessionId),
            effects = listOf(BreathSessionEffect.RequestLocationPermission(state.sessionId)),
        )
    }

    private fun locationPermissionResult(
        state: BreathSessionState,
        event: BreathSessionEvent.LocationPermissionResult,
    ): BreathSessionTransition {
        if (state !is BreathSessionState.RequestingLocationPermission || state.sessionId != event.sessionId) {
            return BreathSessionTransition(state)
        }
        if (!event.granted) {
            return BreathSessionTransition(BreathSessionState.Idle(state.sessionId))
        }
        return BreathSessionTransition(
            state = BreathSessionState.Listening(state.sessionId, 0f, 0f, ZERO),
            effects = listOf(BreathSessionEffect.StartInput(state.sessionId)),
        )
    }

    private fun strengthSample(
        state: BreathSessionState,
        event: BreathSessionEvent.StrengthSample,
    ): BreathSessionTransition {
        val activeState =
            when (state) {
                is BreathSessionState.Listening -> state
                is BreathSessionState.NeedsMore -> state
                is BreathSessionState.Quiet -> state
                else -> return BreathSessionTransition(state)
            }
        if (activeState.sessionId != event.sessionId) return BreathSessionTransition(state)

        val elapsed = event.elapsed.coerceIn(ZERO, config.maxSampleElapsed)
        val strength = event.strength.coerceIn(0f, 1f)
        val activeThreshold =
            if (activeState.growth > 0f) {
                config.sustainThreshold
            } else {
                config.activationThreshold
            }
        val isActive = strength >= activeThreshold
        val growth =
            if (isActive) {
                (activeState.growth + (elapsed / config.growthDuration).toFloat()).coerceAtMost(1f)
            } else {
                activeState.growth
            }
        val quietFor = if (isActive) ZERO else activeState.quietFor + elapsed
        val nextState =
            if (growth >= 1f || quietFor >= config.quietDelay) {
                BreathSessionState.Quiet(activeState.sessionId, growth, strength, quietFor)
            } else {
                BreathSessionState.Listening(activeState.sessionId, growth, strength, quietFor)
            }
        return BreathSessionTransition(nextState)
    }

    private fun release(
        state: BreathSessionState,
        event: BreathSessionEvent.ReleaseRequested,
    ): BreathSessionTransition {
        val activeState =
            when (state) {
                is BreathSessionState.Listening -> state
                is BreathSessionState.NeedsMore -> state
                is BreathSessionState.Quiet -> state
                else -> return BreathSessionTransition(state)
            }
        if (activeState.sessionId != event.sessionId) return BreathSessionTransition(state)
        val reachedReleaseGesture =
            event.upwardDistanceDp >= config.releaseDistanceDp ||
                event.upwardVelocityDpPerSecond >= config.releaseVelocityDpPerSecond
        if (!reachedReleaseGesture) return BreathSessionTransition(activeState)
        if (activeState.growth < config.minimumReleaseProgress) {
            return BreathSessionTransition(
                BreathSessionState.NeedsMore(
                    sessionId = activeState.sessionId,
                    growth = activeState.growth,
                    strength = activeState.strength,
                    quietFor = activeState.quietFor,
                ),
                effects = listOf(BreathSessionEffect.ShowNeedsMore(activeState.sessionId)),
            )
        }
        return BreathSessionTransition(
            state = BreathSessionState.Bursting(activeState.sessionId),
            effects =
                listOf(
                    BreathSessionEffect.StopInput(activeState.sessionId),
                    BreathSessionEffect.StartBurstAnimation(activeState.sessionId),
                ),
        )
    }

    private fun stop(state: BreathSessionState): BreathSessionTransition {
        val sessionId = state.sessionId
        if (state is BreathSessionState.Idle) return BreathSessionTransition(state)
        return BreathSessionTransition(
            state = BreathSessionState.Idle(sessionId),
            effects = listOf(BreathSessionEffect.StopInput(sessionId)),
        )
    }

    private fun burstFinished(
        state: BreathSessionState,
        event: BreathSessionEvent.BurstFinished,
    ): BreathSessionTransition {
        if (state !is BreathSessionState.Bursting || state.sessionId != event.sessionId) {
            return BreathSessionTransition(state)
        }
        return BreathSessionTransition(BreathSessionState.Idle(state.sessionId))
    }

    private fun inputFailed(
        state: BreathSessionState,
        event: BreathSessionEvent.InputFailed,
    ): BreathSessionTransition {
        if (state.sessionId != event.sessionId || state is BreathSessionState.Idle) {
            return BreathSessionTransition(state)
        }
        return BreathSessionTransition(
            state = BreathSessionState.Idle(event.sessionId),
            effects =
                listOf(
                    BreathSessionEffect.StopInput(event.sessionId),
                    BreathSessionEffect.ShowError(event.error),
                ),
        )
    }
}
