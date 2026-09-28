package com.pheeeew.data.remote.emotion

import com.pheeeew.data.remote.group.dto.GroupStampResponseDto
import kotlinx.serialization.Serializable

@Serializable
internal data class EmotionPageDto(
    val items: List<EmotionDto>,
    val hasNext: Boolean,
    val nextCursor: String? = null,
)

@Serializable
internal data class EmotionDto(
    val id: Long,
    val properties: EmotionPropertiesDto,
    val geometry: EmotionPointDto? = null,
)

@Serializable
internal data class EmotionPropertiesDto(
    val state: String,
    val nickname: String,
    val createdAt: String,
    val isMine: Boolean,
    val contentType: String,
    val memo: String? = null,
    val emojis: List<NearbyEmotionEmojiDto>,
    val groupStamp: GroupStampResponseDto? = null,
    val audio: EmotionAudioDto? = null,
)

@Serializable
internal data class NearbyEmotionEmojiDto(
    val type: String,
    val count: Long,
    val selected: Boolean,
)

@Serializable
internal data class EmotionAudioDto(
    val playbackUrl: String,
    val expiresAt: String,
)

@Serializable
internal data class EmotionBlockRequest(
    val emotionId: Long,
)
