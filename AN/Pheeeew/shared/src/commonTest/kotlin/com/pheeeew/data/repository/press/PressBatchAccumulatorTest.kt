package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PressBatchAccumulatorTest {
    @Test
    fun `splits ten thousand taps into bounded batches without losing counts`() {
        val queue = PressBatchAccumulator()
        val expected =
            mapOf(
                EmotionState.FRUSTRATED to 4_000L,
                EmotionState.IRRITATED to 2_000L,
                EmotionState.EXHAUSTED to 3_000L,
                EmotionState.DISCOURAGED to 500L,
                EmotionState.ANGRY to 500L,
            )
        expected.forEach { (emotion, amount) -> assertTrue(queue.add(emotion, amount)) }
        assertEquals(10_000L, queue.size)
        val drained = EmotionState.entries.associateWith { 0L }.toMutableMap()
        var sequence = 1L
        while (queue.isNotEmpty) {
            val next = assertNotNull(queue.take(30, 100, (sequence % 5).toInt(), sequence++))
            assertTrue(next.totalCount <= 100)
            assertTrue(next.counts.values.all { it in 1..30 })
            next.counts.forEach { (emotion, count) -> drained[emotion] = drained.getValue(emotion) + count }
        }
        assertEquals(expected, drained)
        assertEquals(0L, queue.size)
    }

    @Test
    fun `long backlog is narrowed only after limiting request counts`() {
        val queue = PressBatchAccumulator()
        assertTrue(queue.add(EmotionState.ANGRY, Int.MAX_VALUE.toLong() + 42L))
        val next = assertNotNull(queue.take(30, 100, 0, 1))
        assertEquals(30, next.totalCount)
        assertEquals(Int.MAX_VALUE.toLong() + 12L, queue.size)
    }

    @Test
    fun `numeric range rejection does not change the accumulated counts`() {
        val queue = PressBatchAccumulator()
        assertTrue(queue.add(EmotionState.EXHAUSTED, Long.MAX_VALUE))
        assertFalse(queue.add(EmotionState.ANGRY))
        assertEquals(Long.MAX_VALUE, queue.size)
        val next = assertNotNull(queue.take(30, 100, 0, 1))
        assertEquals(mapOf(EmotionState.EXHAUSTED to 30), next.counts)
        assertEquals(Long.MAX_VALUE - 30L, queue.size)
    }
}
