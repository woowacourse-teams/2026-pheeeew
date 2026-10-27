package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.press.CompactRaceCount
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.RaceCountUnit
import com.pheeeew.feature.screens.ranking.press.compactRaceCount
import com.pheeeew.feature.screens.ranking.press.raceProgress
import com.pheeeew.feature.screens.ranking.press.raceTrackPosition
import com.pheeeew.feature.screens.ranking.press.toRaceRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressRaceRowModelTest {
    @Test
    fun zeroAndInvalidCountsStayWithinTrack() {
        assertEquals(0f, raceProgress(0, 0))
        assertEquals(0f, raceProgress(-1, 12))
        assertEquals(1f, raceProgress(15, 12))
        assertEquals(0.5f, raceProgress(6, 12))
        assertEquals(1f, raceProgress(Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(emptyList<PressPersonRank>().toRaceRows().isEmpty())
    }

    @Test
    fun serverOrderAndTiesArePreservedWithoutInferringIdentity() {
        val people =
            listOf(
                PressPersonRank(2, "익명", 9),
                PressPersonRank(2, "익명", 9),
                PressPersonRank(1, "첫째", 12),
            )
        val rows = people.toRaceRows()
        assertEquals(listOf(2L, 2L, 1L), rows.map { it.rank })
        assertEquals(listOf(0.75f, 0.75f, 1f), rows.map { it.progress })
        assertFalse(rows.any { it.isMe })
        assertEquals(people.map { it.count }, rows.map { it.count })
    }

    @Test
    fun largeCountsAreCompactWithoutOverflow() {
        assertEquals(CompactRaceCount("1210", RaceCountUnit.Exact), compactRaceCount(1210))
        assertEquals(CompactRaceCount("9999", RaceCountUnit.Exact), compactRaceCount(9999))
        assertEquals(CompactRaceCount("1", RaceCountUnit.Man), compactRaceCount(10000))
        assertEquals(CompactRaceCount("1.2", RaceCountUnit.Man), compactRaceCount(12999))
        assertEquals(CompactRaceCount("1", RaceCountUnit.Eok), compactRaceCount(100000000))
        assertEquals(CompactRaceCount("922.3", RaceCountUnit.Gyeong), compactRaceCount(Long.MAX_VALUE))
    }

    @Test
    fun trackKeepsLeadersAwayFromAnEndpointAndSmallCountsVisible() {
        assertEquals(0.25f, raceTrackPosition(0f))
        assertEquals(0.7f, raceTrackPosition(1f))
        assertTrue(raceTrackPosition(25f / 1210f) > 0.3f)
        assertTrue(raceTrackPosition(0.5f) < raceTrackPosition(1f))
        assertEquals(0.25f, raceTrackPosition(Float.NaN))
    }
}
