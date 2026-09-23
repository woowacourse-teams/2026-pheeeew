package com.pheeeew.legacy.data.local.device

import com.pheeeew.legacy.domain.model.device.RefreshToken

interface DeviceTokenStorage {
    suspend fun getRefreshToken(): RefreshToken?

    suspend fun saveRefreshToken(refreshToken: RefreshToken)

    suspend fun clear()
}
