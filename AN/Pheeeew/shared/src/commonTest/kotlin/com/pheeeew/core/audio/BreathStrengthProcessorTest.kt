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

        repeat(4) {
            assertEquals(androidProcessor.process(metrics), iosProcessor.process(metrics))
        }
        assertEquals(androidProcessor.process(metrics), iosProcessor.process(metrics))
    }

    @Test
    fun `reset은 이전 smoothing 값을 제거한다`() {
        val processor = BreathStrengthProcessor()
        repeat(4) { processor.process(BreathSignalMetrics(0.1f, 0f, 0f)) }
        val activeScore = processor.process(metrics)

        processor.reset()

        assertEquals(0f, processor.process(BreathSignalMetrics(0f, 0f, 0f)))
        assertTrue(activeScore > 0f)
    }

    @Test
    fun `초기 보정이 기기별 고정 음량 차이를 줄인다`() {
        val quietPhone = BreathStrengthProcessor()
        val sensitivePhone = BreathStrengthProcessor()
        repeat(4) {
            quietPhone.process(BreathSignalMetrics(0.1f, 0f, 0f))
            sensitivePhone.process(BreathSignalMetrics(0.3f, 0f, 0f))
        }

        val quietScore = quietPhone.process(BreathSignalMetrics(0.5f, 0.5f, 0.5f))
        val sensitiveScore = sensitivePhone.process(BreathSignalMetrics(0.6111111f, 0.5f, 0.5f))
        assertTrue(kotlin.math.abs(quietScore - sensitiveScore) < 0.001f)
    }
}
