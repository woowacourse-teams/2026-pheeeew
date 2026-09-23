package com.pheeeew.legacy.core.audio

/** 네이티브 오디오 구현이 common strength processor에 전달하는 scalar 지표입니다. */
data class BreathSignalMetrics(
    val amplitude: Float,
    val lowFrequencyPresence: Float,
    val noisyTexture: Float,
    val speechBandPresence: Float = 0f,
)
