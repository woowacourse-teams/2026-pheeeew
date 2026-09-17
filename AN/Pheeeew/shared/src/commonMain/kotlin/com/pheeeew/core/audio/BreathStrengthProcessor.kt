package com.pheeeew.core.audio

/** 플랫폼이 계산한 metric을 동일한 한숨 strength로 정규화하고 smoothing합니다. */
class BreathStrengthProcessor {
    private var smoothedStrength = 0f
    private val amplitudeNormalizer = BreathAmplitudeNormalizer()

    fun process(metrics: BreathSignalMetrics): Float {
        val normalizedMetrics = metrics.copy(amplitude = amplitudeNormalizer.normalize(metrics.amplitude))
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
        amplitudeNormalizer.reset()
    }
}
