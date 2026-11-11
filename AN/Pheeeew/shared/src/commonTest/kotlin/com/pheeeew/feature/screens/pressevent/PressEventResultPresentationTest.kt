package com.pheeeew.feature.screens.pressevent

import com.pheeeew.domain.model.pressevent.PressEventMyRank
import kotlin.test.Test
import kotlin.test.assertEquals

class PressEventResultPresentationTest {
    @Test
    fun firstAttempt() {
        assertEquals("도전 완료!", eventResultTitle(100, null, true))
        assertEquals("현재 순위 7위", eventResultRank(100, null, true, 7))
    }

    @Test
    fun equalOrLowerDoesNotImprove() {
        val previous = PressEventMyRank(100, 5)
        listOf(100L, 99L).forEach { total ->
            assertEquals("기록 갱신 실패!", eventResultTitle(total, previous, true))
            assertEquals("현재 순위 6위", eventResultRank(total, previous, true, 6))
        }
    }

    @Test
    fun higherShowsRankChange() {
        val previous = PressEventMyRank(100, 5)
        assertEquals("기록 갱신 성공!", eventResultTitle(101, previous, true))
        assertEquals("현재 순위 5위 -> 3위", eventResultRank(101, previous, true, 3))
    }

    @Test
    fun unknownPriorDoesNotClaimFirstAttemptOrImprovement() {
        assertEquals("도전 완료!", eventResultTitle(101, null, false))
        assertEquals("현재 순위 3위", eventResultRank(101, null, false, 3))
    }
}
