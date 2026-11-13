package com.pheeeew.domain.press.settlement

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressWriteLedgerTest {
    @Test
    fun `personal confirmation keeps aggregate optimism until covered by a snapshot`() {
        val ledger = PressWriteLedger()
        ledger.accept(EmotionState.ANGRY)
        ledger.confirm(batch())
        assertEquals(PressWriteCounts(emptyMap(), 1, 0), ledger.snapshot())
        assertEquals(1, ledger.acceptedPressCoverageAtReadStart())
        assertTrue(ledger.reconcileAllSnapshot(AllDailyPressSnapshot("2026-10-20", 10), 1))
        assertEquals(PressWriteCounts(emptyMap(), 0, 0), ledger.snapshot())
    }

    @Test
    fun `a snapshot removes only confirmed coverage captured before later input`() {
        val ledger = PressWriteLedger()
        ledger.accept(EmotionState.ANGRY)
        ledger.confirm(batch())
        val coverage = ledger.acceptedPressCoverageAtReadStart()
        ledger.accept(EmotionState.ANGRY)
        ledger.accept(EmotionState.EXHAUSTED)
        ledger.confirm(batch())
        assertTrue(ledger.reconcileAllSnapshot(AllDailyPressSnapshot("2026-10-20", 10), coverage))
        assertEquals(PressWriteCounts(mapOf(EmotionState.EXHAUSTED to 1L), 2, 1), ledger.snapshot())
        assertEquals(1, ledger.acceptedPressCoverageAtReadStart())
    }

    @Test
    fun `rejection removes only its batch and preserves later input`() {
        val ledger = PressWriteLedger()
        ledger.accept(EmotionState.ANGRY)
        ledger.accept(EmotionState.EXHAUSTED)
        ledger.reject(batch())
        assertEquals(PressWriteCounts(mapOf(EmotionState.EXHAUSTED to 1L), 1, 1), ledger.snapshot())
        assertTrue(ledger.hasOutstanding())
    }

    @Test
    fun `numeric guards account for both unconfirmed and confirmed aggregate optimism`() {
        val ledger = PressWriteLedger()
        ledger.accept(EmotionState.ANGRY)
        assertTrue(ledger.exceedsAcceptanceLimit(Long.MAX_VALUE - 1, 0))
        ledger.confirm(batch())
        assertFalse(ledger.exceedsAcceptanceLimit(Long.MAX_VALUE - 1, 0))
        assertTrue(ledger.exceedsAcceptanceLimit(0, Long.MAX_VALUE - 1))
        assertFalse(ledger.reconcileAllSnapshot(AllDailyPressSnapshot("2026-10-20", Long.MAX_VALUE), 0))
        assertEquals(PressWriteCounts(emptyMap(), 1, 0), ledger.snapshot())
    }

    private fun batch() = PressBatch(mapOf(EmotionState.ANGRY to 1))
}
