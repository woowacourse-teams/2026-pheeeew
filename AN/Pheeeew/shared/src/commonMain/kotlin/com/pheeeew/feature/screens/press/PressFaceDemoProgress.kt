package com.pheeeew.feature.screens.press

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual

/** Temporary per-emotion UI verification policy. Not a server score or durable press counter. */
internal data class PressFaceDemoProgress(
    val inputsInCycle: Int = 0,
    val playbackId: Long = 0,
) {
    init {
        require(inputsInCycle in 0 until CYCLE_INPUTS)
    }

    val visual: EmotionFaceVisual
        get() = EmotionFaceVisual(EmotionFaceStage.progression[inputsInCycle / INPUTS_PER_STAGE], playbackId)

    fun afterInput(accepted: Boolean): PressFaceDemoProgress {
        if (!accepted) return this
        val next = (inputsInCycle + 1) % CYCLE_INPUTS
        val changed = next / INPUTS_PER_STAGE != inputsInCycle / INPUTS_PER_STAGE
        return copy(inputsInCycle = next, playbackId = if (changed) playbackId + 1 else playbackId)
    }

    private companion object {
        const val INPUTS_PER_STAGE = 5
        val CYCLE_INPUTS = INPUTS_PER_STAGE * EmotionFaceStage.progression.size
    }
}
