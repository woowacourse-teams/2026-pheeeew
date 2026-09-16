package com.pheeeew.core.audio

/** 플랫폼이 계산한 metric을 동일한 한숨 strength로 정규화하고 smoothing합니다. */
class BreathStrengthProcessor {
    private var smoothedStrength = 0f

    fun process(metrics: BreathSignalMetrics): Float {
        smoothedStrength =
            BreathStrengthScorer.score(
                amplitude = metrics.amplitude,
                lowFrequencyPresence = metrics.lowFrequencyPresence,
                noisyTexture = metrics.noisyTexture,
                previousSmoothedStrength = smoothedStrength,
            )
        return smoothedStrength
    }

    fun reset() {
        smoothedStrength = 0f
    }
}
