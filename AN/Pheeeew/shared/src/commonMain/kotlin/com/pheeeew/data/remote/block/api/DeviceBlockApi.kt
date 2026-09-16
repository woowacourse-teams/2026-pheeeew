package com.pheeeew.data.remote.block.api

import com.pheeeew.data.remote.block.dto.DeviceBlockCreateRequestDto
import com.pheeeew.data.remote.block.dto.DeviceBlockResponseDto

interface DeviceBlockApi {
    suspend fun create(request: DeviceBlockCreateRequestDto): DeviceBlockResponseDto
}
