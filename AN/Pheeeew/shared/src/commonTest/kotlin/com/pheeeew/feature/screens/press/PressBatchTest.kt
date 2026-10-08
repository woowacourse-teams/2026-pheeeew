package com.pheeeew.feature.screens.press

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PressBatchTest {
    @Test
    fun `splits queued presses within server limits without losing accepted counts`() {
        val accumulator = PressBatchAccumulator(maxOutstandingCount = 300)
        repeat(101) { assertTrue(accumulator.add(EmotionKind.Blocked, location)) }

        val batches =
            buildList {
                while (accumulator.isNotEmpty) {
                    add(assertNotNull(accumulator.take(maxPerEmotion = 30, maxTotal = 100, firstEmotionIndex = size)))
                }
            }

        assertEquals(101, batches.sumOf { it.totalCount })
        assertTrue(batches.all { batch -> batch.totalCount <= 100 && batch.counts.values.all { it <= 30 } })
        assertEquals(101, batches.sumOf { it.counts[EmotionState.FRUSTRATED] ?: 0 })
    }

    @Test
    fun `rejects additional input when the bounded queue reaches its capacity`() {
        val accumulator = PressBatchAccumulator(maxOutstandingCount = 3)

        repeat(3) { assertTrue(accumulator.add(EmotionKind.Angry, location)) }

        assertFalse(accumulator.add(EmotionKind.Angry, location))
        assertEquals(
            3,
            assertNotNull(accumulator.take(maxPerEmotion = 30, maxTotal = 100, firstEmotionIndex = 0)).totalCount,
        )
    }

    @Test
    fun `keeps presses from different coordinates in separate ordered batches`() {
        val accumulator = PressBatchAccumulator(maxOutstandingCount = 10)
        val nextLocation = CurrentLocation(37.6, 127.1, 8f, location.capturedAtMillis + 1_000L)

        assertTrue(accumulator.add(EmotionKind.Blocked, location))
        assertTrue(accumulator.add(EmotionKind.Blocked, location))
        assertTrue(accumulator.add(EmotionKind.Angry, nextLocation))

        val firstBatch = assertNotNull(accumulator.take(maxPerEmotion = 30, maxTotal = 100, firstEmotionIndex = 0))
        val secondBatch = assertNotNull(accumulator.take(maxPerEmotion = 30, maxTotal = 100, firstEmotionIndex = 0))

        assertEquals(location, firstBatch.location)
        assertEquals(mapOf(EmotionState.FRUSTRATED to 2), firstBatch.counts)
        assertEquals(nextLocation, secondBatch.location)
        assertEquals(mapOf(EmotionState.ANGRY to 1), secondBatch.counts)
    }

    private companion object {
        val location = CurrentLocation(37.5, 127.0, 10f, 1_791_416_400_000L)
    }
}
