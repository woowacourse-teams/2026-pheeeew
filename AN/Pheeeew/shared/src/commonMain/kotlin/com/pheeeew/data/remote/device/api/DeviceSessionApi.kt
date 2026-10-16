package com.pheeeew.data.remote.device.api

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.device.dto.DeviceChallengeDto
import com.pheeeew.data.remote.device.dto.DeviceRefreshRequestDto
import com.pheeeew.data.remote.device.dto.DeviceRefreshResponseDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.device.dto.DeviceV3RegistrationRequestDto

interface DeviceSessionApi {
    suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto>

    suspend fun register(request: DeviceV3RegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto> =
        register(DeviceRegistrationRequestDto(request.requestId, request.attestation))

    suspend fun refresh(request: DeviceRefreshRequestDto): ApiResult<DeviceRefreshResponseDto>

    suspend fun challenge(): ApiResult<DeviceChallengeDto>
}
