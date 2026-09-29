package com.pheeeew.feature.screens.map.detail

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel

data class EmotionDetailUiModel(
    val groupName: String,
    val stampText: String,
    val stamp: StampAppearanceUiModel?,
    val nickname: String,
    val emotion: EmotionTypeUiModel,
    val createdAtLabel: String,
    val content: EmotionDetailContentUiModel,
    val actionsEnabled: Boolean,
    val reactions: List<EmotionReactionUiModel>,
    val reactionError: String?,
)

sealed interface EmotionDetailContentUiModel {
    data object Empty : EmotionDetailContentUiModel

    data class Memo(
        val text: String,
    ) : EmotionDetailContentUiModel

    data class Audio(
        val playbackUrl: String,
        val expiresAt: String,
        val durationMillis: Long?,
        val positionMillis: Long,
        val waveform: List<Float>,
        val isPlaying: Boolean,
        val isPreparing: Boolean,
        val error: String?,
    ) : EmotionDetailContentUiModel
}

data class EmotionReactionUiModel(
    val id: String,
    val emoji: String,
    val count: Long,
    val isSelected: Boolean,
)
