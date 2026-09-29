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
    val recordPhrase: String,
    val icon: DrawableResource,
    val recordPrompt: String,
) {
    FRUSTRATED("답답", "답답한", Res.drawable.ic_emotion_frustrated, "아, 속 터져… 왜저래 진짜"),
    IRRITATED("짜증", "짜증난", Res.drawable.ic_emotion_irritated, "에잇! 짜증나!!"),
    EXHAUSTED("지침", "지친", Res.drawable.ic_emotion_exhausted, "아무것도 하기 싫다… 아무것도 안 하는 것도 힘들다…"),
    DISCOURAGED("좌절", "좌절한", Res.drawable.ic_emotion_discouraged, "오늘도 왜 나만 힘들지!?"),
    ANGRY("분노", "분노한", Res.drawable.ic_emotion_angry, "아니, 이건 아니지… 아아아아악!!!!!!"),
}
