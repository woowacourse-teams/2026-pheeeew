package com.pheeeew.feature.screens.group.detail.model

import com.pheeeew.domain.model.emotion.ReactionCount
import org.jetbrains.compose.resources.DrawableResource

data class GroupMoodPostUiModel(
    val id: String,
    val author: String,
    val emotionIcon: DrawableResource,
    val timeAgo: String,
    val isMine: Boolean = false,
    val content: GroupMoodContentUiModel,
    val reactions: List<ReactionCount> = emptyList(),
)

sealed interface GroupMoodContentUiModel {
    data class TextContent(
        val text: String,
    ) : GroupMoodContentUiModel

    data class AudioContent(
        val durationLabel: String,
        val isPlaying: Boolean = false,
    ) : GroupMoodContentUiModel
}
