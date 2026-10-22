package com.pheeeew.data.press.session

import com.pheeeew.data.press.delivery.PressWritePhase
import com.pheeeew.data.press.delivery.PressWriteState
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.press.settlement.PressPendingWrite
import com.pheeeew.domain.press.settlement.PressWriteResolution
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PressWriteSessionTest {
    @Test
    fun `unknown reconciliation and retry preserve original batch baseline and retry age`() {
        val session = PressWriteSession()
        session.accept(EmotionState.ANGRY)
        val original = assertNotNull(session.prepareForSend(snapshot()) { write() })
        session.markAwaitingSnapshot(original)
        assertTrue(session.isAwaitingSnapshot())
        val resolution = assertIs<PressWriteResolution.Pending>(session.resolveUnknown(snapshot(), true))
        assertEquals(1, resolution.write.reconciliationMisses)
        val retry = assertNotNull(session.prepareForSend(snapshot(9)) { error("Must reuse the original batch") })
        assertEquals(original.pending.batch, retry.pending.batch)
        assertEquals(original.pending.baseline, retry.pending.baseline)
        assertEquals(original.createdAtMonotonicMillis, retry.createdAtMonotonicMillis)
        assertEquals(1, retry.pending.reconciliationMisses)
        assertEquals(PressWritePhase.InFlight, retry.phase)
        assertTrue(retry.uncertain)
        assertEquals(0, session.acceptedPressCoverageAtReadStart())
        assertTrue(session.canRetryUnknown(PressMonotonicMillis(999), PressDelayMillis(1_000)))
        assertFalse(session.canRetryUnknown(PressMonotonicMillis(1_000), PressDelayMillis(1_000)))
        assertFalse(session.canRetryUnknown(PressMonotonicMillis(1), null))
    }

    @Test
    fun `definitely unsent retry refreshes its baseline without changing batch identity`() {
        val session = PressWriteSession()
        session.accept(EmotionState.ANGRY)
        val original = assertNotNull(session.prepareForSend(snapshot()) { write() })
        session.markRetryPending(original)
        val retry = assertNotNull(session.prepareForSend(snapshot(9)) { error("Must reuse the original batch") })
        assertEquals(original.pending.batch, retry.pending.batch)
        assertEquals(snapshot(9), retry.pending.baseline)
        assertEquals(original.createdAtMonotonicMillis, retry.createdAtMonotonicMillis)
        assertFalse(retry.uncertain)
    }

    @Test
    fun `unknown confirmation clears delivery state and keeps aggregate coverage`() {
        val session = PressWriteSession()
        session.accept(EmotionState.ANGRY)
        val original = assertNotNull(session.prepareForSend(snapshot()) { write() })
        session.markAwaitingSnapshot(original)
        assertEquals(PressWriteResolution.Confirmed, session.resolveUnknown(snapshot(1), false))
        assertFalse(session.hasPendingWrite())
        assertFalse(session.isOutcomeUnknown())
        assertFalse(session.hasOutstanding())
        assertEquals(1, session.counts().optimisticAllPressCount)
        assertEquals(1, session.acceptedPressCoverageAtReadStart())
    }

    @Test
    fun `retiring an unknown batch preserves later input`() {
        val session = PressWriteSession()
        session.accept(EmotionState.ANGRY)
        val original = assertNotNull(session.prepareForSend(snapshot()) { write() })
        session.markAwaitingSnapshot(original)
        session.accept(EmotionState.EXHAUSTED)
        repeat(2) { assertIs<PressWriteResolution.Pending>(session.resolveUnknown(snapshot(), false)) }
        assertEquals(PressWriteResolution.Retired, session.resolveUnknown(snapshot(), false))
        assertFalse(session.hasPendingWrite())
        assertTrue(session.hasOutstanding())
        assertEquals(mapOf(EmotionState.EXHAUSTED to 1L), session.counts().optimisticCounts)
        assertEquals(1, session.counts().pendingPressCount)
        assertEquals(1, session.counts().optimisticAllPressCount)
    }

    private fun write() =
        PressWriteState(
            pending = PressPendingWrite(PressBatch(1, mapOf(EmotionState.ANGRY to 1)), snapshot()),
            createdAtMonotonicMillis = PressMonotonicMillis(0),
        )

    private fun snapshot(count: Long = 0): MyDailyPressSnapshot =
        MyDailyPressSnapshot(
            "2026-10-20",
            EmotionState.entries.associateWith { if (it == EmotionState.ANGRY) count else 0L },
            count,
        )
}
