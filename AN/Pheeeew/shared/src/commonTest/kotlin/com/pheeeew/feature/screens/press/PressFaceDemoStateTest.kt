package com.pheeeew.feature.screens.press

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressFaceDemoStateTest {
    @Test
    fun acceptedInputsAreIndependentPerEmotionAndRejectionsDoNotCreateVisualState() {
        val state = PressFaceDemoState()
        state.onInput(EmotionKind.Angry, accepted = false)
        assertTrue(state.visuals.isEmpty())
        repeat(4) { state.onInput(EmotionKind.Angry, accepted = true) }
        repeat(3) { state.onInput(EmotionKind.Blocked, accepted = true) }
        repeat(10) { state.onInput(EmotionKind.Angry, accepted = false) }
        assertEquals(EmotionFaceStage.Intact, state.visuals.getValue(EmotionKind.Angry).stage)
        state.onInput(EmotionKind.Angry, accepted = true)
        assertEquals(EmotionFaceStage.Hairline, state.visuals.getValue(EmotionKind.Angry).stage)
        assertEquals(EmotionFaceStage.Intact, state.visuals.getValue(EmotionKind.Blocked).stage)
    }
}
