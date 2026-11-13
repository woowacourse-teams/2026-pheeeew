package com.pheeeew.feature.screens.press

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PressActivityAvatarTest {
    @Test
    fun firstThreePeopleUseFacesAndOnlyTheRemainderUsesTheBadge() {
        for (count in 0L..5L) {
            val state = PressActivityUiState(count, PressActivityStatus.Known)
            assertEquals(minOf(count, 3).toInt(), state.avatarCount)
            assertEquals((count - 3).takeIf { it > 0 }, state.avatarOverflowCount)
        }
        val many = PressActivityUiState(Long.MAX_VALUE, PressActivityStatus.Known)
        assertEquals(3, many.avatarCount)
        assertEquals(Long.MAX_VALUE - 3, many.avatarOverflowCount)
    }

    @Test
    fun unknownAndStaleCountsNeverShowParticipantFaces() {
        for (status in listOf(
            PressActivityStatus.Unknown,
            PressActivityStatus.Loading,
            PressActivityStatus.Reconnecting,
        )) {
            val state = PressActivityUiState(13, status)
            assertEquals(0, state.avatarCount)
            assertNull(state.avatarOverflowCount)
        }
    }
}
