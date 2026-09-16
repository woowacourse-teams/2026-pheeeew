package com.pheeeew.domain.model.device

data class AuthSession(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken,
    val accessTokenExpiresAtEpochSeconds: Long,
) {
    fun isAccessTokenExpired(
        nowEpochSeconds: Long,
        safetyWindowSeconds: Long = 30,
    ): Boolean =
        nowEpochSeconds + safetyWindowSeconds >=
            accessTokenExpiresAtEpochSeconds
}
