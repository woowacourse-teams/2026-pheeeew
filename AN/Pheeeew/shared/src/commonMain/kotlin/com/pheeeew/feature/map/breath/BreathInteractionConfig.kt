package com.pheeeew.feature.map.breath

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 한숨 입력의 사용자 체감 정책을 한 곳에서 관리합니다. */
data class BreathInteractionConfig(
    val effectiveStrengthThreshold: Float = 0.22f,
    val growthDuration: Duration = 2_200.milliseconds,
    val quietDelay: Duration = 500.milliseconds,
    val minimumReleaseGrowth: Float = 0.3f,
    val releaseVelocityThresholdDpPerSecond: Float = 250f,
)
