package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmotionBlockRequestDto(
    val emotionId: Long,
)

@Serializable
data class EmotionBlockResponseDto(
    val emotionId: Long,
    val nickname: String,
    val memo: String? = null,
    val createdAt: String,
)
