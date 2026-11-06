package com.pheeeew.feature.screens.ranking

import com.pheeeew.core.time.koreaCalendarDate
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingUiState
import com.pheeeew.feature.screens.ranking.press.forRankingDate
import com.pheeeew.feature.screens.ranking.press.rankingDateLabel
import com.pheeeew.feature.screens.ranking.press.rankingMonthDays
import com.pheeeew.feature.screens.ranking.press.rankingWeekday
import com.pheeeew.feature.screens.ranking.press.shiftRankingDate
import com.pheeeew.feature.screens.ranking.press.shiftRankingMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RankingDateTest {
    @Test
    fun `자정 이전 결과를 오늘로 바꾸지 않고 오류는 재시도 가능하게 유지한다`() {
        val old = PressRankingUiState(date = "2026-11-05", status = PressRankingStatus.Ready)
        assertEquals(PressRankingStatus.Loading, old.forRankingDate("2026-11-06").status)
        assertEquals(PressRankingStatus.Failed, old.copy(hasRefreshError = true).forRankingDate("2026-11-06").status)
        assertEquals(
            PressRankingStatus.Failed,
            PressRankingUiState(status = PressRankingStatus.Failed).forRankingDate("2026-11-06").status,
        )
    }

    @Test
    fun `연도와 윤년 경계를 이동한다`() {
        assertEquals("2025-12-31", shiftRankingDate("2026-01-01", -1))
        assertEquals("2024-02-29", shiftRankingDate("2024-03-01", -1))
        assertEquals("2025-02-28", shiftRankingDate("2025-03-01", -1))
        assertEquals("2025-12", shiftRankingMonth("2026-01", -1))
        assertEquals("2027-01", shiftRankingMonth("2026-12", 1))
        assertEquals(29, rankingMonthDays("2024-02").size)
        assertEquals(28, rankingMonthDays("2025-02").size)
        assertEquals(30, rankingMonthDays("2026-11").size)
    }

    @Test
    fun `시안의 요일과 한국 자정 기준을 따른다`() {
        assertEquals(0, rankingWeekday("2026-11-01"))
        assertEquals("2026.11.04 (수)", rankingDateLabel("2026-11-04"))
        assertEquals("2026-11-06", koreaCalendarDate(Instant.parse("2026-11-05T15:00:00Z").toEpochMilliseconds()))
        assertEquals("2026-11-05", koreaCalendarDate(Instant.parse("2026-11-05T14:59:59Z").toEpochMilliseconds()))
    }
}
