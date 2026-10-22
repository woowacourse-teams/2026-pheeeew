package com.pheeeew.domain.press.settlement

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PressReconcilerTest {
    @Test
    fun `a larger total alone cannot confirm the wrong emotion`() {
        val write = write()
        val wrongEmotion = snapshot(mapOf(EmotionState.EXHAUSTED to 2L))
        val decision =
            assertIs<PressWriteResolution.Pending>(PressReconciler.resolveUnknown(write, wrongEmotion, false))
        assertEquals(1, decision.write.reconciliationMisses)
        assertEquals(0, write.reconciliationMisses)
        assertEquals(write.batch, decision.write.batch)
        assertEquals(
            PressWriteResolution.Confirmed,
            PressReconciler.resolveUnknown(write, snapshot(mapOf(EmotionState.ANGRY to 2L)), false),
        )
    }

    @Test
    fun `third missing snapshot retires a batch only when safe replay is unavailable`() {
        val write = write().copy(reconciliationMisses = 2)
        assertEquals(PressWriteResolution.Retired, PressReconciler.resolveUnknown(write, snapshot(), false))
        val pending = assertIs<PressWriteResolution.Pending>(PressReconciler.resolveUnknown(write, snapshot(), true))
        assertEquals(3, pending.write.reconciliationMisses)
        assertEquals(write.batch, pending.write.batch)
    }

    @Test
    fun `day rollover never confirms the prior day even with matching counts`() {
        val nextDay = snapshot(mapOf(EmotionState.ANGRY to 2L), "2026-10-21")
        assertEquals(PressWriteResolution.Retired, PressReconciler.resolveUnknown(write(), nextDay, true))
    }

    @Test
    fun `numeric overflow cannot falsely confirm a batch`() {
        val full = snapshot(mapOf(EmotionState.ANGRY to Long.MAX_VALUE))
        assertIs<PressWriteResolution.Pending>(
            PressReconciler.resolveUnknown(write().copy(baseline = full), full, false),
        )
    }

    @Test
    fun `aggregate subtracts only coverage captured at request start`() {
        val result =
            PressReconciler.reconcileAll(
                100,
                acceptedPressesSinceSnapshot = 7,
                optimisticAggregateCount = 10,
                acceptedPressesCoveredAtReadStart = 5,
            )
        assertEquals(PressAggregateCounts(2, 5), result)
        assertNull(PressReconciler.reconcileAll(Long.MAX_VALUE, 7, 10, 5))
    }

    private fun write() =
        PressPendingWrite(
            batch = PressBatch(mapOf(EmotionState.ANGRY to 2)),
            baseline = snapshot(),
        )

    private fun snapshot(
        counts: Map<EmotionState, Long> = emptyMap(),
        date: String = "2026-10-20",
    ): MyDailyPressSnapshot {
        val complete = EmotionState.entries.associateWith { counts[it] ?: 0L }
        return MyDailyPressSnapshot(date, complete, complete.values.sum())
    }
}
