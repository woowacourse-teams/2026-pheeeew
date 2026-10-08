package com.pheeeew.data.remote.press

import kotlinx.serialization.Serializable

@Serializable
internal data class MyDailyPressResponseDto(
    val pressDate: String,
    val counts: Map<String, Long>,
    val total: Long,
)

@Serializable
internal data class AllDailyPressResponseDto(
    val pressDate: String,
    val total: Long,
)

@Serializable
internal data class EmotionPressWriteRequestDto(
    val counts: Map<String, Int>,
)

@Serializable
internal data class EmotionPressWriteResponseDto(
    val counts: Map<String, Long>,
    val total: Long,
)
