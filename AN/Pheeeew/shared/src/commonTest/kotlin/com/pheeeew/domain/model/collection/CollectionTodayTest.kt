package com.pheeeew.domain.model.collection

import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionTodayTest {
    @Test
    fun `can claim from aggregate counts across emotions`() {
        val today =
            today(
                presses = listOf(24, 20, 18, 16, 22),
                stamps = listOf(0, 0, 1, 0, 0),
            )

        assertEquals(100L, today.totalPressCount)
        assertEquals(1L, today.totalStampCount)
        assertTrue(today.canClaim)
    }

    @Test
    fun `cannot claim until both aggregate thresholds are reached`() {
        val noStamp = today(presses = listOf(100, 0, 0, 0, 0), stamps = listOf(0, 0, 0, 0, 0))
        val notEnoughPresses = today(presses = listOf(99, 0, 0, 0, 0), stamps = listOf(1, 0, 0, 0, 0))

        assertFalse(noStamp.canClaim)
        assertFalse(notEnoughPresses.canClaim)
    }

    @Test
    fun `claim emotion uses presses plus ten points per stamp`() {
        val today =
            today(
                presses = listOf(49, 54, 0, 0, 0),
                stamps = listOf(1, 0, 0, 0, 0),
            )

        assertEquals(EmotionState.FRUSTRATED, today.claimEmotion)
    }

    @Test
    fun `claim emotion tie follows emotion order`() {
        val today =
            today(
                presses = listOf(20, 20, 0, 0, 0),
                stamps = listOf(0, 0, 0, 0, 0),
                reverseItems = true,
            )

        assertEquals(EmotionState.FRUSTRATED, today.claimEmotion)
    }

    @Test
    fun `already claimed today hides further claim`() {
        val today =
            today(
                presses = listOf(20, 20, 20, 20, 20),
                stamps = listOf(1, 0, 0, 0, 0),
                firstClaimed = true,
            )

        assertTrue(today.hasClaimedCard)
        assertFalse(today.canClaim)
    }

    private fun today(
        presses: List<Long>,
        stamps: List<Long>,
        reverseItems: Boolean = false,
        firstClaimed: Boolean = false,
    ): CollectionToday {
        val emotions = if (reverseItems) EmotionState.entries.reversed() else EmotionState.entries
        return CollectionToday(
            date = "2026-10-26",
            items =
                emotions.mapIndexed { index, emotion ->
                    val pressCount = presses[emotion.ordinal]
                    val stampCount = stamps[emotion.ordinal]
                    CollectionTodayItem(
                        state = emotion,
                        stamps = CollectionActivityProgress(stampCount, 1, (1 - stampCount).coerceAtLeast(0)),
                        presses = CollectionActivityProgress(pressCount, 100, (100 - pressCount).coerceAtLeast(0)),
                        card =
                            if (firstClaimed && index == 0) {
                                CollectionCardReference(
                                    cardId = "card-$index",
                                    date = "2026-10-26",
                                    state = emotion,
                                    images = CollectionImages("character", "round", "straight"),
                                )
                            } else {
                                null
                            },
                    )
                },
        )
    }
}
