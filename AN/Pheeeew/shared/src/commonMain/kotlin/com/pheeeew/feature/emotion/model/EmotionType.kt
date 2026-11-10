package com.pheeeew.feature.emotion.model

import androidx.compose.ui.graphics.Color
import com.pheeeew.core.designsystem.theme.AppColors
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
    FRUSTRATED(Res.string.emotion_label_frustrated, AppColors.Emotion.Mood.Frustrated),
    IRRITATED(Res.string.emotion_label_irritated, AppColors.Emotion.Mood.Irritated),
    EXHAUSTED(Res.string.emotion_label_exhausted, AppColors.Emotion.Mood.Exhausted),
    DISCOURAGED(Res.string.emotion_label_discouraged, AppColors.Emotion.Mood.Discouraged),
    ANGRY(Res.string.emotion_label_angry, AppColors.Emotion.Mood.Angry),
}
