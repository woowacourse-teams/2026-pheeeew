package com.pheeeew.feature.screens.press

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PressFaceDemoStateTest {
    @Test
    fun initialFacesStayIntactOnFirstInputAndRejectionsDoNotChangeThem() {
        val state = PressFaceDemoState()
        val initial = state.visuals
        assertEquals(EmotionKind.entries.toSet(), initial.keys)
        initial.values.forEach { assertEquals(EmotionFaceStage.Intact, it.stage) }
        state.onInput(EmotionKind.Angry, accepted = false)
        assertEquals(initial, state.visuals)
        state.onInput(EmotionKind.Angry, accepted = true)
        assertEquals(initial, state.visuals)
        repeat(3) { state.onInput(EmotionKind.Angry, accepted = true) }
        repeat(3) { state.onInput(EmotionKind.Blocked, accepted = true) }
        repeat(10) { state.onInput(EmotionKind.Angry, accepted = false) }
        assertEquals(EmotionFaceStage.Intact, state.visuals.getValue(EmotionKind.Angry).stage)
        state.onInput(EmotionKind.Angry, accepted = true)
        assertEquals(EmotionFaceStage.Hairline, state.visuals.getValue(EmotionKind.Angry).stage)
        assertEquals(EmotionFaceStage.Intact, state.visuals.getValue(EmotionKind.Blocked).stage)
    }
}
