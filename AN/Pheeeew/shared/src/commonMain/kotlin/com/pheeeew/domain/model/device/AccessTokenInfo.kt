package com.pheeeew.domain.model.device

data class AccessTokenInfo(
    val accessToken: AccessToken,
    val accessTokenExpiresAtEpochSeconds: Long,
    val refreshToken: RefreshToken? = null,
)
