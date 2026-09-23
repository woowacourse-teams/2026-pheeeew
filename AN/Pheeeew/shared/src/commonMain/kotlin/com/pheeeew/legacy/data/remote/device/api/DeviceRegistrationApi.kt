package com.pheeeew.legacy.data.remote.device.api

import com.pheeeew.legacy.data.remote.device.dto.DeviceChallengeResponseDto
import com.pheeeew.legacy.data.remote.device.dto.DeviceRegistrationRequestDto
import com.pheeeew.legacy.data.remote.device.dto.DeviceRegistrationResponseDto
import com.pheeeew.legacy.data.remote.device.dto.RefreshTokenRequestDto
import com.pheeeew.legacy.data.remote.device.dto.RefreshTokenResponseDto

interface DeviceRegistrationApi {
    suspend fun getChallenge(): DeviceChallengeResponseDto

    suspend fun register(request: DeviceRegistrationRequestDto): DeviceRegistrationResponseDto

    suspend fun refresh(request: RefreshTokenRequestDto): RefreshTokenResponseDto
}
