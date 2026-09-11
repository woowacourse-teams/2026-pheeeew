package com.pheeeew.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathStrengthProcessorTest {
    private val metrics = BreathSignalMetrics(amplitude = 0.8f, lowFrequencyPresence = 0.4f, noisyTexture = 0.2f)

    @Test
    fun `같은 metric은 플랫폼과 무관하게 같은 score를 만든다`() {
        val androidProcessor = BreathStrengthProcessor()
        val iosProcessor = BreathStrengthProcessor()

        assertEquals(androidProcessor.process(metrics), iosProcessor.process(metrics))
    }

    @Test
    fun `reset은 이전 smoothing 값을 제거한다`() {
        val processor = BreathStrengthProcessor()
        val activeScore = processor.process(metrics)

        processor.reset()

        assertEquals(0f, processor.process(BreathSignalMetrics(0f, 0f, 0f)))
        assertTrue(activeScore > 0f)
    }
}
