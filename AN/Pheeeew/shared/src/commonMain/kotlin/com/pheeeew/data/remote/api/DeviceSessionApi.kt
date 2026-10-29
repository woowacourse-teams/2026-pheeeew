package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.dto.DeviceChallengeDto
import com.pheeeew.data.remote.dto.DeviceRefreshRequestDto
import com.pheeeew.data.remote.dto.DeviceRefreshResponseDto
import com.pheeeew.data.remote.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.dto.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.dto.DeviceV3RegistrationRequestDto

interface DeviceSessionApi {
    suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto>

    suspend fun register(request: DeviceV3RegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto>

    suspend fun refresh(request: DeviceRefreshRequestDto): ApiResult<DeviceRefreshResponseDto>

    suspend fun challenge(): ApiResult<DeviceChallengeDto>
}
