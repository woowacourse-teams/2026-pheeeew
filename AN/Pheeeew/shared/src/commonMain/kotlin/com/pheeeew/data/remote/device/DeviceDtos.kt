package com.pheeeew.data.remote.device

import kotlinx.serialization.Serializable

@Serializable
class DeviceAttestationDto(
    val platform: String,
    val token: String? = null,
    val challenge: String? = null,
    val keyId: String? = null,
) {
    override fun toString(): String = "DeviceAttestationDto(<redacted>)"
}

@Serializable
class DeviceRegistrationRequestDto(
    val requestId: String,
    val attestation: DeviceAttestationDto,
) {
    override fun toString(): String = "DeviceRegistrationRequestDto(<redacted>)"
}

@Serializable
class DeviceRegistrationResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
) {
    override fun toString(): String = "DeviceRegistrationResponseDto(<redacted>)"
}

@Serializable
class DeviceRefreshRequestDto(
    val refreshToken: String,
) {
    override fun toString(): String = "DeviceRefreshRequestDto(<redacted>)"
}

@Serializable
class DeviceRefreshResponseDto(
    val accessToken: String,
    val expiresIn: Long,
) {
    override fun toString(): String = "DeviceRefreshResponseDto(<redacted>)"
}

@Serializable
class DeviceChallengeDto(
    val challenge: String,
    val expiresIn: Long,
) {
    override fun toString(): String = "DeviceChallengeDto(<redacted>)"
}
