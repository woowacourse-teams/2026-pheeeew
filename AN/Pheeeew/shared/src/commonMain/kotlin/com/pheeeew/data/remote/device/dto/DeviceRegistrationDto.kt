package com.pheeeew.data.remote.device.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceChallengeResponseDto(val challenge: String, val expiresIn: Long)

@Serializable
data class DeviceAttestationDto(
    val platform: String,
    val token: String? = null,
    val challenge: String? = null,
    val keyId: String? = null,
)

@Serializable
data class DeviceRegistrationRequestDto(
    val requestId: String,
    val attestation: DeviceAttestationDto? = null,
)

@Serializable
data class DeviceRegistrationResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

@Serializable
data class RefreshTokenRequestDto(val refreshToken: String)

@Serializable
data class RefreshTokenResponseDto(val accessToken: String, val expiresIn: Long)
