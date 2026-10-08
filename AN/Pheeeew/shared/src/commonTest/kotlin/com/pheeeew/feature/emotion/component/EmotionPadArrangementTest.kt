package com.pheeeew.feature.emotion.component

import kotlin.test.Test
import kotlin.test.assertEquals

class EmotionPadArrangementTest {
    @Test
    fun `two three arrangement centers first row and places remaining emotions below`() {
        assertEquals(
            listOf(
                EmotionButtonOrigin(62.25f, 0f),
                EmotionButtonOrigin(186.75f, 0f),
                EmotionButtonOrigin(0f, 177f),
                EmotionButtonOrigin(124.5f, 177f),
                EmotionButtonOrigin(249f, 177f),
            ),
            EmotionPadArrangement.TwoThree.buttonOrigins,
        )
    }
}
