package com.pheeeew.feature.screens.map.record

import org.jetbrains.compose.resources.DrawableResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated

enum class EmotionTypeUiModel(
    val label: String,
    val icon: DrawableResource,
) {
    FRUSTRATED("답답", Res.drawable.ic_emotion_frustrated),
    IRRITATED("짜증", Res.drawable.ic_emotion_irritated),
    EXHAUSTED("지침", Res.drawable.ic_emotion_exhausted),
    DISCOURAGED("좌절", Res.drawable.ic_emotion_discouraged),
    ANGRY("분노", Res.drawable.ic_emotion_angry),
}
