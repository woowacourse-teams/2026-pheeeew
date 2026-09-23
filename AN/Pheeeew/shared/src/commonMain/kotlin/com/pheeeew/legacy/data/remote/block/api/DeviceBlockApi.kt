package com.pheeeew.legacy.data.remote.block.api

import com.pheeeew.legacy.data.remote.block.dto.DeviceBlockCreateRequestDto
import com.pheeeew.legacy.data.remote.block.dto.DeviceBlockResponseDto

interface DeviceBlockApi {
    suspend fun create(request: DeviceBlockCreateRequestDto): DeviceBlockResponseDto
}
