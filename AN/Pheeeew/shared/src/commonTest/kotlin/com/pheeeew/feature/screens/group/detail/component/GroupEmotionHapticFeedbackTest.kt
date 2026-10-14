package com.pheeeew.feature.screens.group.detail.component

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupEmotionHapticFeedbackTest {
    @Test
    fun `accepted tap triggers one haptic and one visual reaction`() {
        var haptics = 0
        var visualReactions = 0

        dispatchGroupEmotionTapFeedback(
            accepted = true,
            fixtureFeedbackOnAcceptedPress = false,
            hapticFeedback = { haptics++ },
            visualFeedback = { visualReactions++ },
        )

        assertEquals(1, haptics)
        assertEquals(1, visualReactions)
    }

    @Test
    fun `rapid accepted taps trigger one haptic for each accepted tap`() {
        var haptics = 0
        var visualReactions = 0

        repeat(12) {
            dispatchGroupEmotionTapFeedback(
                accepted = true,
                fixtureFeedbackOnAcceptedPress = false,
                hapticFeedback = { haptics++ },
                visualFeedback = { visualReactions++ },
            )
        }

        assertEquals(12, haptics)
        assertEquals(12, visualReactions)
    }

    @Test
    fun `rejected tap triggers neither haptic nor visual reaction`() {
        var haptics = 0
        var visualReactions = 0

        dispatchGroupEmotionTapFeedback(
            accepted = false,
            fixtureFeedbackOnAcceptedPress = false,
            hapticFeedback = { haptics++ },
            visualFeedback = { visualReactions++ },
        )

        assertEquals(0, haptics)
        assertEquals(0, visualReactions)
    }

    @Test
    fun `accepted preview fixture can show visuals without device haptics`() {
        var haptics = 0
        var visualReactions = 0

        dispatchGroupEmotionTapFeedback(
            accepted = true,
            fixtureFeedbackOnAcceptedPress = true,
            hapticFeedback = { haptics++ },
            visualFeedback = { visualReactions++ },
        )

        assertEquals(0, haptics)
        assertEquals(1, visualReactions)
    }
}
