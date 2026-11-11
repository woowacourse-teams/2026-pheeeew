package com.pheeeew.feature.screens.pressevent

import com.pheeeew.domain.model.pressevent.PressEventGamePhase
import com.pheeeew.domain.model.pressevent.PressEventGameSnapshot
import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.toUiKind

/** Derive the demo face cycle from accepted counts rather than keeping a second mutable counter. */
internal fun eventFaceVisuals(counts: Map<EmotionKind, Long>): Map<EmotionKind, EmotionFaceVisual> =
    EmotionKind.entries.associateWith { kind ->
        val count = counts[kind] ?: 0L
        val stageChanges = count / 5L
        EmotionFaceVisual(
            stage = EmotionFaceStage.progression[(stageChanges % EmotionFaceStage.progression.size).toInt()],
            playbackId = stageChanges,
        )
    }

internal fun PressEventGameSnapshot.toUiState(): PressEventUiState =
    PressEventUiState(
        phase =
            when (phase) {
                PressEventGamePhase.Ready -> PressEventPhase.Ready
                PressEventGamePhase.Countdown -> PressEventPhase.Countdown
                PressEventGamePhase.Playing -> PressEventPhase.Playing
                PressEventGamePhase.Finished -> PressEventPhase.Finished
            },
        seconds = seconds,
        counts = counts.mapKeys { it.key.toUiKind() }.mapValues { it.value.toLong() },
    )
