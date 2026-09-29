package com.pheeeew.core.network

/**
 * Adapter implemented at the app composition root around the device-session owner, then passed to
 * [createPlatformApiClient]. The caller owns token storage and refresh policy.
 */
fun interface AccessTokenProvider {
    suspend fun accessToken(): AccessToken?
}

/** Redacts the token from accidental string interpolation and logs. */
class AccessToken(
    value: String,
    val generation: Long = 0,
) {
    val value: String =
        value.also {
            require(it.isNotBlank()) { "Access token은 비어 있을 수 없습니다." }
        }

    override fun toString(): String = "AccessToken(<redacted>)"
}
