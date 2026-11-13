package com.pheeeew.feature.component.emotion

import com.pheeeew.domain.model.emotion.ReactionCount
import org.jetbrains.compose.resources.DrawableResource

data class EmotionChatItemUiModel(
    val nickname: String,
    val isMine: Boolean,
    val emotionIcon: DrawableResource,
    val emotionDescription: String?,
    val timeLabel: String,
    val content: EmotionChatContentUiModel,
    val reactions: List<ReactionCount>,
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
