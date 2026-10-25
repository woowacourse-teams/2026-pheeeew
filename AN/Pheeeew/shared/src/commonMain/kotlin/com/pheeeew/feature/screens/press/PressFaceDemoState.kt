package com.pheeeew.feature.screens.press

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind

/** Route-local demo only; remove this supplier when the final visual-input policy is selected. */
@Stable
internal class PressFaceDemoState {
    private val progress = mutableMapOf<EmotionKind, PressFaceDemoProgress>()
    var visuals: Map<EmotionKind, EmotionFaceVisual> by mutableStateOf(emptyMap())
        private set

    fun onInput(
        kind: EmotionKind,
        accepted: Boolean,
    ) {
        if (!accepted) return
        val next = (progress[kind] ?: PressFaceDemoProgress()).afterInput(accepted = true)
        progress[kind] = next
        if (visuals[kind] != next.visual) visuals = visuals + (kind to next.visual)
    }
}

@Composable
internal fun rememberPressFaceDemoState(): PressFaceDemoState = remember { PressFaceDemoState() }
