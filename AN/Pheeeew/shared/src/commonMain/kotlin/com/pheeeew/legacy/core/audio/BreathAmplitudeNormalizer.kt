package com.pheeeew.legacy.core.audio

/** 시작 직후 주변 소음을 기준으로 입력 amplitude를 기기 독립적인 상대값으로 변환합니다. */
class BreathAmplitudeNormalizer(
    private val calibrationSamples: Int = 4,
    private val calibrationAmplitudeThreshold: Float = 0.35f,
    private val maxCalibrationFrames: Int = calibrationSamples * 4,
) {
    private var collectedSamples = 0
    private var calibrationFrames = 0
    private var calibrationComplete = false
    private var noiseFloor = 0f

    init {
        require(calibrationSamples > 0) { "calibrationSamples must be positive" }
        require(calibrationAmplitudeThreshold in 0f..1f) {
            "calibrationAmplitudeThreshold must be between 0 and 1"
        }
        require(maxCalibrationFrames > 0) { "maxCalibrationFrames must be positive" }
    }

    fun normalize(amplitude: Float): Float {
        val clampedAmplitude = amplitude.coerceIn(0f, 1f)
        if (!calibrationComplete) {
            calibrationFrames += 1
            if (clampedAmplitude <= calibrationAmplitudeThreshold) {
                noiseFloor =
                    if (collectedSamples == 0) {
                        clampedAmplitude
                    } else {
                        noiseFloor + (clampedAmplitude - noiseFloor) / (collectedSamples + 1)
                    }
                collectedSamples += 1
                if (collectedSamples >= calibrationSamples) {
                    calibrationComplete = true
                }
                return 0f
            }

            val normalized = relativeAmplitude(clampedAmplitude)
            if (calibrationFrames >= maxCalibrationFrames) {
                calibrationComplete = true
                if (collectedSamples == 0) {
                    noiseFloor = calibrationAmplitudeThreshold
                }
            }
            return normalized.coerceIn(0f, 1f)
        }
        return relativeAmplitude(clampedAmplitude)
            .coerceIn(0f, 1f)
    }

    private fun relativeAmplitude(amplitude: Float): Float =
        (amplitude - noiseFloor) / (1f - noiseFloor).coerceAtLeast(0.01f)

    fun reset() {
        collectedSamples = 0
        calibrationFrames = 0
        calibrationComplete = false
        noiseFloor = 0f
    }
}
