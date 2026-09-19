package com.pheeeew.core.audio

/** 플랫폼이 계산한 metric을 동일한 한숨 strength로 정규화하고 smoothing합니다. */
class BreathStrengthProcessor {
    private var smoothedStrength = 0f
    private val amplitudeNormalizer = BreathAmplitudeNormalizer()

    internal var lastNormalizedAmplitude: Float = 0f
        private set

    fun process(metrics: BreathSignalMetrics): Float {
        lastNormalizedAmplitude = amplitudeNormalizer.normalize(metrics.amplitude)
        val normalizedMetrics = metrics.copy(amplitude = lastNormalizedAmplitude)
        smoothedStrength =
            BreathStrengthScorer.score(
                amplitude = normalizedMetrics.amplitude,
                lowFrequencyPresence = normalizedMetrics.lowFrequencyPresence,
                noisyTexture = normalizedMetrics.noisyTexture,
                speechBandPresence = normalizedMetrics.speechBandPresence,
                previousSmoothedStrength = smoothedStrength,
            )
        return smoothedStrength
    }

    fun reset() {
        smoothedStrength = 0f
        lastNormalizedAmplitude = 0f
        amplitudeNormalizer.reset()
    }
}
