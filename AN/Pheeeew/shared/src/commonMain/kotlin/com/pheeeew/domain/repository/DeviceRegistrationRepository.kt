package com.pheeeew.domain.repository

import com.pheeeew.domain.model.device.AccessTokenInfo
import com.pheeeew.domain.model.device.AuthSession
import com.pheeeew.domain.model.device.RefreshToken

interface DeviceRegistrationRepository {
    suspend fun getStoredRefreshToken(): RefreshToken?
    suspend fun register(): AuthSession
    suspend fun refreshAccessToken(
        refreshToken: RefreshToken,
    ): AccessTokenInfo

    suspend fun clearCredentials()
}
