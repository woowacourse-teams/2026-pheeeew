package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressReconcilerTest {
    @Test
    fun `a larger total alone cannot confirm the wrong emotion`() {
        val write = write()
        val wrongEmotion = snapshot(mapOf(EmotionState.EXHAUSTED to 2L))
        val decision =
            assertIs<PressWriteResolution.Pending>(PressReconciler.resolveUnknown(write, wrongEmotion, false))
        assertEquals(1, decision.write.reconciliationMisses)
        assertEquals(0, write.reconciliationMisses)
        assertEquals(PressWritePhase.RetryPending, decision.write.phase)
        assertTrue(decision.write.uncertain)
        assertEquals("same-request", decision.write.batch.requestId)
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
                acceptedSinceSnapshot = 7,
                optimisticCount = 10,
                coveredAtReadStart = 5,
            )
        assertEquals(PressAggregateCounts(2, 5), result)
        assertNull(PressReconciler.reconcileAll(Long.MAX_VALUE, 7, 10, 5))
    }

    @Test
    fun `a detached aggregate is invalid after a newer read or an active or uncertain write`() {
        assertTrue(PressReconciler.canApplyAggregate(4, 4, null))
        assertFalse(PressReconciler.canApplyAggregate(3, 4, null))
        assertFalse(PressReconciler.canApplyAggregate(4, 4, write().copy(uncertain = false)))
        assertFalse(PressReconciler.canApplyAggregate(4, 4, write().copy(phase = PressWritePhase.RetryPending)))
        assertTrue(
            PressReconciler.canApplyAggregate(
                4,
                4,
                write().copy(phase = PressWritePhase.RetryPending, uncertain = false),
            ),
        )
    }

    private fun write() =
        PressWriteState(
            batch = PressBatch(1, mapOf(EmotionState.ANGRY to 2), requestId = "same-request"),
            baseline = snapshot(),
            createdAtMillis = 0,
            uncertain = true,
        )

    private fun snapshot(
        counts: Map<EmotionState, Long> = emptyMap(),
        date: String = "2026-10-20",
    ): MyDailyPressSnapshot {
        val complete = EmotionState.entries.associateWith { counts[it] ?: 0L }
        return MyDailyPressSnapshot(date, complete, complete.values.sum())
    }
}
