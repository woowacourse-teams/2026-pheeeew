package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.RefreshToken

interface DeviceTokenStorage {
    suspend fun getRefreshToken(): RefreshToken?
    suspend fun saveRefreshToken(refreshToken: RefreshToken)
    suspend fun clear()
}
