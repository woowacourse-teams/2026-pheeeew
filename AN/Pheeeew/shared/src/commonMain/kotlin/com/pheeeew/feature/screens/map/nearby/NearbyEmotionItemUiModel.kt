package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionAudio
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
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
    val audio: EmotionAudio?,
    val coordinate: GeoCoordinate? = null,
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
        audio,
        coordinate,
    )
