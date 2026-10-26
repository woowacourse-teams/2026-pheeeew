package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

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
data class CollectionClaimRequestDto(
    val date: String,
    val state: String,
)
