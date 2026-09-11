package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInputError

/** reducer가 요청하고 state holder가 실행하는 일회성 효과입니다. */
sealed interface BreathSessionEffect {
    data class RequestMicrophonePermission(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class RequestLocationPermission(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class StartInput(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class StopInput(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class StartBurstAnimation(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class ShowNeedsMore(
        val sessionId: Long,
    ) : BreathSessionEffect

    data class ShowError(
        val error: BreathInputError,
    ) : BreathSessionEffect
}
