package com.pheeeew.domain.usecase

import com.pheeeew.data.remote.block.api.DeviceBlockApi
import com.pheeeew.data.remote.block.dto.DeviceBlockCreateRequestDto
import com.pheeeew.data.remote.block.dto.DeviceBlockResponseDto

class BlockUserUseCase(
    private val api: DeviceBlockApi,
) {
    suspend operator fun invoke(sighId: Long): DeviceBlockResponseDto {
        require(sighId > 0) { "한숨 식별자는 양수여야 합니다." }
        return api.create(DeviceBlockCreateRequestDto(sighId = sighId))
    }
}
