package com.pheeeew.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathAmplitudeNormalizerTest {
    @Test
    fun `calibration ignores loud input but keeps it usable`() {
        val normalizer = BreathAmplitudeNormalizer(calibrationSamples = 2)

        assertEquals(0f, normalizer.normalize(0.04f))
        assertTrue(normalizer.normalize(0.9f) > 0.8f)
        assertEquals(0f, normalizer.normalize(0.06f))
        assertTrue(normalizer.normalize(0.9f) > 0.8f)
    }

    @Test
    fun `quiet samples still establish a noise floor`() {
        val normalizer = BreathAmplitudeNormalizer(calibrationSamples = 2)

        normalizer.normalize(0.04f)
        normalizer.normalize(0.06f)

        assertTrue(normalizer.normalize(0.2f) > 0.15f)
    }
}
