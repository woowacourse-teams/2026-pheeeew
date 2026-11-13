package com.pheeeew.feature.screens.press

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import kotlin.test.Test
import kotlin.test.assertEquals

class PressFaceDemoProgressTest {
    @Test
    fun fiveAcceptedInputsAdvanceEachStageAndThirtyReset() {
        var state = PressFaceDemoProgress()
        val expectedStages =
            listOf(
                EmotionFaceStage.Intact,
                EmotionFaceStage.Hairline,
                EmotionFaceStage.Cracked,
                EmotionFaceStage.Fractured,
                EmotionFaceStage.Shattered,
                EmotionFaceStage.Burst,
                EmotionFaceStage.Intact,
            )
        repeat(30) { index ->
            state = state.afterInput(accepted = true)
            val count = index + 1
            assertEquals(expectedStages[count / 5], state.visual.stage)
            assertEquals((count / 5).toLong(), state.visual.playbackId)
        }
        assertEquals(0, state.inputsInCycle)
    }

    @Test
    fun rejectedInputsDoNotAdvanceAndExtraBurstInputsDoNotReplay() {
        var state = PressFaceDemoProgress()
        repeat(25) { state = state.afterInput(accepted = true) }
        val burst = state.visual
        repeat(4) {
            assertEquals(state, state.afterInput(accepted = false))
            state = state.afterInput(accepted = true)
            assertEquals(burst, state.visual)
        }
    }
}
