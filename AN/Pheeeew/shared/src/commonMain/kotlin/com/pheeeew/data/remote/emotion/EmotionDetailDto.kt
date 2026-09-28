package com.pheeeew.data.remote.emotion

import kotlinx.serialization.Serializable

@Serializable
data class EmotionDetailDto(
    val id: Long,
    val geometry: EmotionPointDto,
    val properties: EmotionDetailPropertiesDto,
)

@Serializable
data class EmotionDetailPropertiesDto(
    val createdAt: String,
    val state: String,
    val nickname: String,
    val contentType: String,
    val emojis: List<EmotionEmojiDto>,
    val isMine: Boolean,
    val memo: String? = null,
    val audio: EmotionPlaybackDto? = null,
    val groupStamp: EmotionGroupStampDto? = null,
    val groupId: String? = null,
)

@Serializable
data class EmotionEmojiDto(
    val type: String,
    val count: Long,
    val selected: Boolean,
)

@Serializable
data class EmotionPlaybackDto(
    val playbackUrl: String,
    val expiresAt: String,
)
