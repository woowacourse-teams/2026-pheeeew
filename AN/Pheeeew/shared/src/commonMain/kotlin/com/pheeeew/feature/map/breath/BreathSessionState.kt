package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInputError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** 플랫폼 입력과 Compose 화면 사이에서 공유하는 한숨 세션 상태입니다. */
sealed interface BreathSessionState {
    val sessionId: Long
    val growth: Float
    val strength: Float
    val quietFor: Duration

    data object Idle : BreathSessionState {
        override val sessionId: Long = 0L
        override val growth: Float = 0f
        override val strength: Float = 0f
        override val quietFor: Duration = ZERO
    }

    data class RequestingPermission(
        override val sessionId: Long,
    ) : BreathSessionState {
        override val growth: Float = 0f
        override val strength: Float = 0f
        override val quietFor: Duration = ZERO
    }

    data class Listening(
        override val sessionId: Long,
        override val growth: Float,
        override val strength: Float,
        override val quietFor: Duration,
    ) : BreathSessionState

    data class NeedsMore(
        override val sessionId: Long,
        override val growth: Float,
        override val strength: Float,
        override val quietFor: Duration,
    ) : BreathSessionState

    data class Quiet(
        override val sessionId: Long,
        override val growth: Float,
        override val strength: Float,
        override val quietFor: Duration,
    ) : BreathSessionState

    data class Bursting(
        override val sessionId: Long,
    ) : BreathSessionState {
        override val growth: Float = 0f
        override val strength: Float = 0f
        override val quietFor: Duration = ZERO
    }
}

/** 상태 머신에 전달되는 플랫폼 독립 이벤트입니다. */
sealed interface BreathSessionEvent {
    data object StartRequested : BreathSessionEvent

    data class PermissionResult(
        val sessionId: Long,
        val granted: Boolean,
    ) : BreathSessionEvent

    data class StrengthSample(
        val sessionId: Long,
        val strength: Float,
        val elapsed: Duration,
    ) : BreathSessionEvent

    data class ReleaseRequested(
        val sessionId: Long,
        val upwardDistanceDp: Float,
        val upwardVelocityDpPerSecond: Float,
    ) : BreathSessionEvent

    data object CancelRequested : BreathSessionEvent

    data object LifecycleStopped : BreathSessionEvent

    data class InputFailed(
        val sessionId: Long,
        val error: BreathInputError,
    ) : BreathSessionEvent
}
