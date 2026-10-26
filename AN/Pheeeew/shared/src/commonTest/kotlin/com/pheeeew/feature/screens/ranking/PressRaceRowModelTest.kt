package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.raceProgress
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
        assertEquals(listOf(2, 2, 1), rows.map { it.rank })
        assertEquals(listOf(0.75f, 0.75f, 1f), rows.map { it.progress })
        assertFalse(rows.any { it.isMe })
        assertEquals(people.map { it.count }, rows.map { it.count })
    }
}
