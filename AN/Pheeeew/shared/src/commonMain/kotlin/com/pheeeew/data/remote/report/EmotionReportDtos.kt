package com.pheeeew.data.remote.report

import kotlinx.serialization.Serializable

@Serializable
data class EmotionReportCreateRequestDto(
    val emotionId: Long,
    val reason: String,
)

@Serializable
data class EmotionReportResponseDto(
    val id: Long,
    val emotionId: Long,
    val reason: String,
    val createdAt: String,
)
