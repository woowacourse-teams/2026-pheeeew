package com.pheeeew.legacy.core.audio

object BreathStrengthScorer {
    fun score(
        amplitude: Float,
        lowFrequencyPresence: Float,
        noisyTexture: Float,
        previousSmoothedStrength: Float,
        speechBandPresence: Float = 0f,
    ): Float {
        val audible = amplitude >= 0.08f
        val clampedAmplitude = amplitude.coerceIn(0f, 1f)
        val clampedLowFrequencyPresence = lowFrequencyPresence.coerceIn(0f, 1f)
        val clampedNoisyTexture = noisyTexture.coerceIn(0f, 1f)
        val clampedSpeechBandPresence = speechBandPresence.coerceIn(0f, 1f)
        val loudSpeechFactor = ((clampedAmplitude - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val voicedSpeechPresence =
            clampedLowFrequencyPresence * (1f - clampedNoisyTexture) *
                clampedSpeechBandPresence * loudSpeechFactor
        val raw =
            if (audible) {
                (
                    clampedAmplitude * (
                        0.25f + clampedLowFrequencyPresence * 0.50f +
                            clampedNoisyTexture * 0.25f
                    ) - clampedSpeechBandPresence * 0.05f - voicedSpeechPresence * 0.60f
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
