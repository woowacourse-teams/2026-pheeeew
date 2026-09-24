package com.pheeeew.feature.screens.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_annoyed
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_stuck

enum class EmotionTypeUiModel(
    val label: String,
    val icon: DrawableResource,
) {
    Stuck("답답", Res.drawable.ic_emotion_stuck),
    Annoyed("짜증", Res.drawable.ic_emotion_annoyed),
    Exhausted("지침", Res.drawable.ic_emotion_exhausted),
    Frustrated("좌절", Res.drawable.ic_emotion_frustrated),
    Angry("분노", Res.drawable.ic_emotion_angry),
}
