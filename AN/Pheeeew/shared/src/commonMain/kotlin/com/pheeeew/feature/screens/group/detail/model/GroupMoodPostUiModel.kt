package com.pheeeew.feature.screens.group.detail.model

import com.pheeeew.domain.model.emotion.EmotionAudio
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import org.jetbrains.compose.resources.DrawableResource

data class GroupMoodPostUiModel(
    val id: String,
    val author: String,
    val emotionIcon: DrawableResource,
    val emotionDescription: String?,
    val timeAgo: String,
    val isMine: Boolean = false,
    val content: GroupMoodContentUiModel,
    val reactions: List<ReactionCount> = emptyList(),
    val stamp: StampAppearanceUiModel?,
    val isAudioLoading: Boolean = false,
    val isReactionBusy: Boolean = false,
)

sealed interface GroupMoodContentUiModel {
    data class TextContent(
        val text: String,
    ) : GroupMoodContentUiModel

    data class AudioContent(
        val durationLabel: String?,
        val audio: EmotionAudio?,
        val isPlaying: Boolean = false,
    ) : GroupMoodContentUiModel
}
