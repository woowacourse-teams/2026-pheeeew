package com.pheeeew.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceNicknameResponseDto(
    val nickname: String?,
)

@Serializable
data class DeviceNicknameUpdateRequestDto(
    val nickname: String,
)

@Serializable
data class DeviceNicknameAvailabilityResponseDto(
    val available: Boolean,
)
