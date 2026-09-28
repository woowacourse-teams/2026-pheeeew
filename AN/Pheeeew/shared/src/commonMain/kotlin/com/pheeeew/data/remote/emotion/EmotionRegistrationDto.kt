package com.pheeeew.data.remote.emotion

import kotlinx.serialization.Serializable

@Serializable
internal data class EmotionRegistrationRequestDto(
    val requestId: String,
    val state: String,
    val longitude: Double,
    val latitude: Double,
    val rotationDegrees: Double,
    val contentType: String,
    val memo: String?,
    val audioUploadId: String?,
    val groupId: String?,
)

@Serializable
internal data class EmotionRegistrationResponseDto(
    val id: Long,
)
