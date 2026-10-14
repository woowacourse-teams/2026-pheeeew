package com.pheeeew.feature.component.emotion

import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import org.jetbrains.compose.resources.DrawableResource

data class EmotionChatItemUiModel(
    val nickname: String,
    val isMine: Boolean,
    val emotionIcon: DrawableResource,
    val emotionDescription: String?,
    val timeLabel: String,
    val content: EmotionChatContentUiModel,
    val reactions: List<ReactionCount>,
    val stamp: StampAppearanceUiModel?,
)

sealed interface EmotionChatContentUiModel {
    data class Text(
        val value: String?,
    ) : EmotionChatContentUiModel

    data object EmotionOnly : EmotionChatContentUiModel

    data class Audio(
        val durationLabel: String?,
    ) : EmotionChatContentUiModel
}

enum class EmotionActionsMenuMode {
    Actions,
    Reactions,
}
