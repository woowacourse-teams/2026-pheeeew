package com.pheeeew.feature.component.emotion

import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import org.jetbrains.compose.resources.DrawableResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated

internal val EmotionReactionType.glyph: String
    get() =
        when (this) {
            EmotionReactionType.HEART -> "❤️"
            EmotionReactionType.LAUGH -> "🤣"
            EmotionReactionType.CRY -> "😭"
            EmotionReactionType.DIZZY -> "😵‍💫"
            EmotionReactionType.RAGE -> "🤬"
            EmotionReactionType.SKULL -> "☠️"
        }

internal val EmotionReactionType.label: String
    get() =
        when (this) {
            EmotionReactionType.HEART -> "하트"
            EmotionReactionType.LAUGH -> "웃음"
            EmotionReactionType.CRY -> "눈물"
            EmotionReactionType.DIZZY -> "어지러움"
            EmotionReactionType.RAGE -> "분노"
            EmotionReactionType.SKULL -> "해골"
        }

internal val EmotionState.face: DrawableResource
    get() =
        when (this) {
            EmotionState.FRUSTRATED -> Res.drawable.ic_emotion_frustrated
            EmotionState.IRRITATED -> Res.drawable.ic_emotion_irritated
            EmotionState.EXHAUSTED -> Res.drawable.ic_emotion_exhausted
            EmotionState.DISCOURAGED -> Res.drawable.ic_emotion_discouraged
            EmotionState.ANGRY -> Res.drawable.ic_emotion_angry
        }

internal val EmotionState.label: String
    get() =
        when (this) {
            EmotionState.FRUSTRATED -> "답답"
            EmotionState.IRRITATED -> "짜증"
            EmotionState.EXHAUSTED -> "지침"
            EmotionState.DISCOURAGED -> "좌절"
            EmotionState.ANGRY -> "분노"
        }
