package com.pheeeew.feature.emotion.model

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_label_angry
import pheeeew.shared.generated.resources.emotion_label_discouraged
import pheeeew.shared.generated.resources.emotion_label_exhausted
import pheeeew.shared.generated.resources.emotion_label_frustrated
import pheeeew.shared.generated.resources.emotion_label_irritated

enum class EmotionType(
    val label: StringResource,
    val representativeColor: Color,
) {
    FRUSTRATED(Res.string.emotion_label_frustrated, Color(0xFFF6BBAA)),
    IRRITATED(Res.string.emotion_label_irritated, Color(0xFFF0BFD0)),
    EXHAUSTED(Res.string.emotion_label_exhausted, Color(0xFFB6A1DC)),
    DISCOURAGED(Res.string.emotion_label_discouraged, Color(0xFFBBD9E9)),
    ANGRY(Res.string.emotion_label_angry, Color(0xFFEAA397)),
}
