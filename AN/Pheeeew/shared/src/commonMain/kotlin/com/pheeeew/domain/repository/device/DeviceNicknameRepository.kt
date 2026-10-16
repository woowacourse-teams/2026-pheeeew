package com.pheeeew.domain.repository.device

import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameState
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import kotlinx.coroutines.flow.StateFlow

interface DeviceNicknameRepository {
    val nicknameState: StateFlow<DeviceNicknameState>

    suspend fun findMyNickname(): DeviceNicknameLookup

    suspend fun checkAvailability(nickname: String): DeviceNicknameAvailability

    suspend fun updateNickname(nickname: String): DeviceNicknameUpdateResult
}
