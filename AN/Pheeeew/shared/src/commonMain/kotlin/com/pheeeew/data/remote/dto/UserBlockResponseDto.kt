package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserBlockResponseDto(
    val blockId: Long,
    val emotionId: Long,
    val nickname: String,
    val memo: String? = null,
    val createdAt: String,
)
