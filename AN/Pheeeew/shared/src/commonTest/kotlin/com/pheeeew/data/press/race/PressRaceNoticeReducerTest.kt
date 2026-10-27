package com.pheeeew.data.press.race

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressRaceNoticeReducerTest {
    @Test fun zeroDoesNotClearOrExtendButSameNumbersOnNewVersionReplaceTheOccurrence() {
        val reducer = PressRaceNoticeReducer()
        reducer.accept(frame("1", baseline = true), 1, 0)
        val first = reducer.accept(frame("2", passed = 2, passedBy = 3), 1, 100)!!
        assertEquals(2L, first.passed)
        assertEquals(3L, first.passedBy)
        assertEquals(first, reducer.accept(frame("3"), 1, 200))
        assertFalse(reducer.expire(first.id, 2_099))
        val next = reducer.accept(frame("4", passed = 2, passedBy = 3), 1, 2_000)!!
        assertTrue(next.id > first.id)
        assertFalse(reducer.expire(first.id, 4_000))
        assertFalse(reducer.expire(next.id, 3_999))
        assertTrue(reducer.expire(next.id, 4_000))
        assertNull(reducer.notice)
    }

    @Test fun duplicateOrEarlierVersionDoesNotRenewTheDeadline() {
        val reducer = PressRaceNoticeReducer()
        reducer.accept(frame("9223372036854775808", baseline = true), 1, 0)
        val first = reducer.accept(frame("9223372036854775809", passed = 7), 1, 10)
        assertEquals(first, reducer.accept(frame("9223372036854775809", passed = 7), 1, 1_000))
        assertEquals(first, reducer.accept(frame("2", passedBy = 10), 1, 1_500))
        assertTrue(reducer.expire(first!!.id, 2_010))
    }

    @Test fun entryRequiresAnObservedOrdinaryParticipationTransitionAndNeverInfersFromRank() {
        val reducer = PressRaceNoticeReducer()
        reducer.accept(frame("1", baseline = true, participating = false), 1, 0)
        val entered = reducer.accept(frame("2", participating = true, passed = 1), 1, 100)!!
        assertTrue(entered.entered)
        assertEquals(1L, entered.passed)
        assertEquals(entered, reducer.accept(frame("3", participating = true), 1, 200))
        reducer.accept(frame("4", baseline = true, participating = true), 1, 300)
        assertNull(reducer.accept(frame("5", participating = true), 1, 400))
    }

    @Test fun baselineNewConnectionDateViewAndResetNeverReplayOldAlerts() {
        val reducer = PressRaceNoticeReducer()
        reducer.accept(frame("1", baseline = true), 1, 0)
        assertNull(reducer.accept(frame("2"), 1, 1))
        assertTrue(reducer.accept(frame("3", passed = 2), 1, 2) != null)
        assertNull(reducer.accept(frame("4", baseline = true), 1, 3))
        assertNull(reducer.accept(frame("5", passed = 2), 2, 4))
        assertNull(reducer.accept(frame("6", passed = 2).copy(date = "2026-10-28"), 2, 5))
        assertNull(reducer.accept(frame("7", passed = 2).copy(view = PressRaceView.ALL), 2, 6))
        reducer.reset()
        assertNull(reducer.accept(frame("8", participating = true, passed = 2), 2, 7))
    }

    private fun frame(
        version: String,
        baseline: Boolean = false,
        participating: Boolean = true,
        passed: Long = 0,
        passedBy: Long = 0,
    ) = PressRaceSnapshot(
        "s",
        "c",
        "2026-10-27",
        version,
        "2026-10-27T00:00:00Z",
        PressRaceView.PRESS,
        1,
        PressRaceParticipant(null, null, 0, null, participating),
        emptyList(),
        emptyList(),
        baseline,
        PressRaceOvertakes(passed, passedBy),
    )
}
