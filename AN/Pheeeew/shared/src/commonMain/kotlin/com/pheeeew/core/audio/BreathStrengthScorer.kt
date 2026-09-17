package com.pheeeew.core.audio

object BreathStrengthScorer {
    fun score(
        amplitude: Float,
        lowFrequencyPresence: Float,
        noisyTexture: Float,
        previousSmoothedStrength: Float,
        speechBandPresence: Float = 0f,
    ): Float {
        val audible = amplitude >= 0.08f
        val raw =
            if (audible) {
                (
                    amplitude.coerceIn(0f, 1f) * (
                        0.25f + lowFrequencyPresence.coerceIn(0f, 1f) * 0.50f +
                            noisyTexture.coerceIn(0f, 1f) * 0.25f
                    ) - speechBandPresence.coerceIn(0f, 1f) * 0.20f
                ).coerceAtLeast(0f)
            } else {
                0f
            }
        return if (audible) {
            previousSmoothedStrength.coerceIn(0f, 1f) * 0.55f + raw * 0.45f
        } else {
            0f
        }
    }
}
