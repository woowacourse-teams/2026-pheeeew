package com.pheeeew.feature.map.breath

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

/** 한숨 입력의 사용자 체감 정책을 한 곳에서 관리합니다. */
data class BreathInteractionConfig(
    val activationThreshold: Float = 0.18f,
    val sustainThreshold: Float = 0.12f,
    val growthDuration: Duration = 1_400.milliseconds,
    val quietDelay: Duration = 700.milliseconds,
    val minimumReleaseProgress: Float = 0.2f,
    val releaseDistanceDp: Float = 48f,
    val releaseVelocityDpPerSecond: Float = 180f,
    val maxSampleElapsed: Duration = 200.milliseconds,
) {
    init {
        require(activationThreshold in 0f..1f) { "activationThreshold must be between 0 and 1" }
        require(sustainThreshold in 0f..1f) { "sustainThreshold must be between 0 and 1" }
        require(activationThreshold >= sustainThreshold) {
            "activationThreshold must be greater than or equal to sustainThreshold"
        }
        require(growthDuration > ZERO) { "growthDuration must be positive" }
        require(quietDelay >= ZERO) { "quietDelay must not be negative" }
        require(minimumReleaseProgress in 0f..1f) { "minimumReleaseProgress must be between 0 and 1" }
        require(releaseDistanceDp >= 0f) { "releaseDistanceDp must not be negative" }
        require(releaseVelocityDpPerSecond >= 0f) { "releaseVelocityDpPerSecond must not be negative" }
        require(maxSampleElapsed > ZERO) { "maxSampleElapsed must be positive" }
    }
}
