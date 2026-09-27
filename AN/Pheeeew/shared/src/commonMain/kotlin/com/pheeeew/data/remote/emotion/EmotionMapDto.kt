package com.pheeeew.data.remote.emotion

import kotlinx.serialization.Serializable

@Serializable
data class EmotionMapPageDto(
    val items: List<EmotionMapFeatureDto> = emptyList(),
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
data class EmotionMapFeatureDto(
    val type: String? = null,
    val id: Long,
    val geometry: EmotionPointDto,
    val properties: EmotionMapPropertiesDto,
)

@Serializable
data class EmotionPointDto(
    val type: String? = null,
    val coordinates: List<Double>,
)

@Serializable
data class EmotionMapPropertiesDto(
    val createdAt: String,
    val state: String,
    val rotationDegrees: Double,
    val groupStamp: EmotionGroupStampDto? = null,
)

@Serializable
data class EmotionGroupStampDto(
    val text: String,
    val textColor: String,
    val backgroundColor: String,
    val frame: String,
)
