package com.pheeeew.core.audio

/** 시작 직후 주변 소음을 기준으로 입력 amplitude를 기기 독립적인 상대값으로 변환합니다. */
class BreathAmplitudeNormalizer(
    private val calibrationSamples: Int = 4,
) {
    private var collectedSamples = 0
    private var noiseFloor = 0f

    init {
        require(calibrationSamples > 0) { "calibrationSamples must be positive" }
    }

    fun normalize(amplitude: Float): Float {
        val clampedAmplitude = amplitude.coerceIn(0f, 1f)
        if (collectedSamples < calibrationSamples) {
            noiseFloor =
                if (collectedSamples == 0) {
                    clampedAmplitude
                } else {
                    noiseFloor + (clampedAmplitude - noiseFloor) / (collectedSamples + 1)
                }
            collectedSamples += 1
            return 0f
        }
        return ((clampedAmplitude - noiseFloor) / (1f - noiseFloor).coerceAtLeast(0.01f))
            .coerceIn(0f, 1f)
    }

    fun reset() {
        collectedSamples = 0
        noiseFloor = 0f
    }
}
