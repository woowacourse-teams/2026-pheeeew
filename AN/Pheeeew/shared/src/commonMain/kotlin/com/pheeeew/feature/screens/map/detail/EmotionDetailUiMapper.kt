package com.pheeeew.feature.screens.map.detail

import com.pheeeew.domain.model.emotion.EmotionDetail
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlin.time.Clock

internal fun EmotionDetail.toUiModel() =
    EmotionDetailUiModel(
        groupName = groupStamp?.text ?: "그룹 없음",
        stampText = groupStamp?.text.orEmpty(),
        stamp =
            groupStamp?.let {
                StampAppearanceUiModel(it.text, it.frame.toUiShape(), it.backgroundColor.argb, it.textColor.argb)
            },
        nickname = nickname,
        emotion = EmotionTypeUiModel.valueOf(state.name),
        createdAtLabel = formatEmotionCreatedAt(createdAt, Clock.System.now()),
        content =
            when {
                audio != null -> {
                    EmotionDetailContentUiModel.Audio(
                        playbackUrl = audio.url,
                        expiresAt = audio.expiresAt,
                        durationMillis = null,
                        positionMillis = 0,
                        waveform = emptyList(),
                        isPlaying = false,
                        isPreparing = true,
                        error = null,
                    )
                }

                !memo.isNullOrBlank() -> {
                    EmotionDetailContentUiModel.Memo(memo)
                }

                else -> {
                    EmotionDetailContentUiModel.Empty
                }
            },
        actionsEnabled = true,
        reactionError = null,
        reactions =
            EmotionReactionType.entries.map { type ->
                val reaction = reactions.first { it.type == type }
                EmotionReactionUiModel(
                    type.name,
                    when (type) {
                        EmotionReactionType.HEART -> "❤️"
                        EmotionReactionType.LAUGH -> "🤣"
                        EmotionReactionType.CRY -> "😭"
                        EmotionReactionType.DIZZY -> "😵‍💫"
                        EmotionReactionType.RAGE -> "🤬"
                        EmotionReactionType.SKULL -> "☠️"
                    },
                    reaction.count,
                    reaction.selected,
                )
            },
    )
