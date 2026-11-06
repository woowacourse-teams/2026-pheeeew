package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CollectionImagesDto(
    val characterUrl: String,
    val roundStampUrl: String,
    val straightStampUrl: String,
)

@Serializable
data class CollectionCardReferenceDto(
    val cardId: String,
    val date: String,
    val state: String,
    val images: CollectionImagesDto,
)

@Serializable
data class CollectionCardDto(
    val cardId: String,
    val date: String,
    val state: String,
    val stampCount: Long,
    val pressCount: Long,
    val images: CollectionImagesDto,
)

@Serializable
data class CollectionActivityProgressDto(
    val count: Long,
    val target: Long,
    val remaining: Long,
)

@Serializable
data class CollectionTodayItemDto(
    val state: String,
    val stamps: CollectionActivityProgressDto,
    val presses: CollectionActivityProgressDto,
    val card: CollectionCardReferenceDto? = null,
)

@Serializable
data class CollectionTodayDto(
    val date: String,
    val items: List<CollectionTodayItemDto>,
)

@Serializable
data class CollectionCountsDto(
    val total: Long,
    val byEmotion: Map<String, Long>,
)

@Serializable
data class CollectionHistoryDto(
    val nickname: String? = null,
    val items: List<CollectionCardDto>,
    val counts: CollectionCountsDto,
    val hasNext: Boolean,
    val nextCursor: String? = null,
)

@Serializable
data class CollectionCalendarDto(
    val items: List<CollectionCardDto>,
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
data class CollectionEmotionAudioDto(
    val playbackUrl: String,
    val expiresAt: String,
)

@Serializable
data class CollectionEmotionDto(
    val id: Long,
    val createdAt: String,
    val state: String,
    val contentType: String,
    val memo: String? = null,
    val audio: CollectionEmotionAudioDto? = null,
    val emojis: List<CollectionEmotionEmojiDto> = emptyList(),
)

@Serializable
data class CollectionEmotionEmojiDto(
    val type: String,
    val count: Long,
    val selected: Boolean,
)

@Serializable
data class CollectionEmotionPageDto(
    val items: List<CollectionEmotionDto>,
    val hasNext: Boolean,
    val nextCursor: String? = null,
)

@Serializable
data class CollectionDailyDto(
    val date: String,
    val card: JsonObject? = null,
    val pressCount: Long,
    val emotions: CollectionEmotionPageDto,
)

@Serializable
data class CollectionClaimRequestDto(
    val date: String,
    val state: String,
)
