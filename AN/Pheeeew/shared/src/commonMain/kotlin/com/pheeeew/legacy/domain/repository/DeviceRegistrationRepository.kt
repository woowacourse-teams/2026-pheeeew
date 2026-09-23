package com.pheeeew.legacy.domain.repository

import com.pheeeew.legacy.domain.model.device.AccessTokenInfo
import com.pheeeew.legacy.domain.model.device.AuthSession
import com.pheeeew.legacy.domain.model.device.RefreshToken

interface DeviceRegistrationRepository {
    suspend fun getStoredRefreshToken(): RefreshToken?

    suspend fun register(): AuthSession

    suspend fun refreshAccessToken(refreshToken: RefreshToken): AccessTokenInfo

    suspend fun clearCredentials()
}
