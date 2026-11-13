package com.pheeeew.data.press.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressRaceInputLedgerTest {
    private fun batch(
        id: String = "id",
        count: Int = 100,
    ): PressRaceInputBatch {
        val input = PressRacePendingInput(id, "original", mapOf("ANGRY" to count))
        return PressRaceInputBatch(input, PressRaceProtocol.submit(input))
    }

    private fun ready(
        results: List<PressRaceInputResult> = emptyList(),
        count: Long = 0,
    ) = PressRaceReady("server", "current", "2026-10-27", mapOf("ANGRY" to count), results)

    @Test fun duplicateAndUnknownResultsNeverAddTwice() {
        val ledger = PressRaceInputLedger()
        ledger.ready(ready())
        ledger.add(batch())
        val result = PressRaceInputResult("id", "APPLIED", "2026-10-27", mapOf("ANGRY" to 100L))
        assertTrue(ledger.settle(result))
        assertFalse(ledger.settle(result))
        assertEquals(100L, ledger.confirmed["ANGRY"])
        assertTrue(ledger.pending.isEmpty())
    }

    @Test fun recoveryBaselineIncludesAppliedAndOldDateDoesNotChangeToday() {
        val ledger = PressRaceInputLedger()
        ledger.add(batch())
        ledger.ready(ready(listOf(PressRaceInputResult("id", "APPLIED", "2026-10-27", mapOf("ANGRY" to 100L))), 100))
        assertEquals(100L, ledger.confirmed["ANGRY"])
        ledger.add(batch("yesterday"))
        ledger.settle(PressRaceInputResult("yesterday", "APPLIED", "2026-10-26", mapOf("ANGRY" to 100L)))
        assertEquals(100L, ledger.confirmed["ANGRY"])
    }

    @Test fun unresolvedResultsKeepOriginalInputAndFirstAttempt() {
        val ledger = PressRaceInputLedger()
        ledger.add(batch())
        ledger.attempted("id", 10)
        ledger.attempted("id", 1_000)
        ledger.settle(PressRaceInputResult("id", "NOT_FOUND"))
        assertEquals(10L, ledger.batches.single().firstAttempt)
        assertEquals("original", ledger.pending.single().connectionGeneration)
        assertFalse(ledger.expired(20_009))
        assertTrue(ledger.expired(20_010))
        ledger.discardUnsent()
        assertEquals(1, ledger.batches.size)
    }

    @Test fun capacityIncludesCompleteUtf8PayloadAndExactLimitsAreAccepted() {
        val ledger = PressRaceInputLedger()
        repeat(20) { ledger.add(batch("$it")) }
        assertFalse(ledger.canStore(batch("21")))
        val bytes = PressRaceInputLedger()
        val exact = batch().copy(text = "한".repeat(21_845) + "a")
        assertEquals(65_536, exact.bytes)
        assertTrue(bytes.canStore(exact))
        bytes.add(exact)
        assertFalse(bytes.canStore(batch("extra")))
    }

    @Test fun malformedResultDoesNotRemoveInputOrReplaceBaseline() {
        val ledger = PressRaceInputLedger()
        ledger.ready(ready(count = 5))
        ledger.add(batch())
        val wrong = PressRaceInputResult("id", "APPLIED", "2026-10-27", mapOf("ANGRY" to 99L))
        assertFailsWith<IllegalArgumentException> { ledger.ready(ready(listOf(wrong), 999)) }
        assertEquals(5L, ledger.confirmed["ANGRY"])
        assertEquals(1, ledger.batches.size)
    }

    @Test fun totalOverflowDoesNotPartiallyCommitSettlement() {
        val ledger = PressRaceInputLedger()
        ledger.ready(ready().copy(counts = mapOf("EXHAUSTED" to Long.MAX_VALUE)))
        ledger.add(batch(count = 1))
        assertFailsWith<IllegalArgumentException> {
            ledger.settle(PressRaceInputResult("id", "APPLIED", "2026-10-27", mapOf("ANGRY" to 1L)))
        }
        assertEquals(mapOf("EXHAUSTED" to Long.MAX_VALUE), ledger.confirmed)
        assertEquals(1, ledger.batches.size)
    }

    @Test fun completeCommandAllowsExactlySixteenKiB() {
        val emptySize = batch("a").bytes
        val exact = batch("a".repeat(16 * 1024 - emptySize + 1))
        assertEquals(16 * 1024, exact.bytes)
        assertFailsWith<IllegalArgumentException> { batch(exact.input.requestId + "a") }
    }

    @Test fun protocolAllowsOneHundredOfOneEmotionButRejectsEmptyAndOversize() {
        assertTrue(batch().text.contains("100"))
        assertFailsWith<IllegalArgumentException> { batch(count = 101) }
        assertFailsWith<IllegalArgumentException> { batch(count = 0) }
        assertFailsWith<IllegalArgumentException> { batch("한".repeat(6_000)) }
        val result =
            PressRaceProtocol.decode(
                """{"v":1,"type":"input.result","serverGeneration":"s","connectionGeneration":"c","requestId":"id","status":"NOT_FOUND"}""",
            )
        assertTrue(result is PressRaceMessage.InputResult)
    }
}
