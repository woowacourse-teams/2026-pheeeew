package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import kotlin.time.Instant

data class NearbyEmotionItemUiModel(
    val id: Long,
    val state: EmotionState,
    val nickname: String,
    val createdAt: Instant,
    val isMine: Boolean,
    val contentType: EmotionContentType,
    val memo: String?,
    val reactions: List<ReactionCount>,
    val stamp: StampAppearanceUiModel?,
)

internal fun Emotion.toUiModel() =
    NearbyEmotionItemUiModel(
        id,
        state,
        nickname,
        createdAt,
        isMine,
        contentType,
        memo,
        reactions,
        groupStamp?.let {
            StampAppearanceUiModel(
                it.text,
                it.frame.toUiShape(),
                it.backgroundColor.argb,
                it.textColor.argb,
            )
        },
    )
