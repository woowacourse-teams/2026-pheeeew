package com.pheeeew.core.audio

/** 시작 직후 주변 소음을 기준으로 입력 amplitude를 기기 독립적인 상대값으로 변환합니다. */
class BreathAmplitudeNormalizer(
    private val calibrationSamples: Int = 4,
    private val calibrationAmplitudeThreshold: Float = 0.35f,
) {
    private var collectedSamples = 0
    private var noiseFloor = 0f

    init {
        require(calibrationSamples > 0) { "calibrationSamples must be positive" }
        require(calibrationAmplitudeThreshold in 0f..1f) {
            "calibrationAmplitudeThreshold must be between 0 and 1"
        }
    }

    fun normalize(amplitude: Float): Float {
        val clampedAmplitude = amplitude.coerceIn(0f, 1f)
        if (collectedSamples < calibrationSamples) {
            if (clampedAmplitude <= calibrationAmplitudeThreshold) {
                noiseFloor =
                    if (collectedSamples == 0) {
                        clampedAmplitude
                    } else {
                        noiseFloor + (clampedAmplitude - noiseFloor) / (collectedSamples + 1)
                    }
                collectedSamples += 1
                return 0f
            }
        }
        return relativeAmplitude(clampedAmplitude)
            .coerceIn(0f, 1f)
    }

    private fun relativeAmplitude(amplitude: Float): Float =
        (amplitude - noiseFloor) / (1f - noiseFloor).coerceAtLeast(0.01f)

    fun reset() {
        collectedSamples = 0
        noiseFloor = 0f
    }
}
