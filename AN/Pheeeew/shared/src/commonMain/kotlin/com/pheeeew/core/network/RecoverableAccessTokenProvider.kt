package com.pheeeew.core.network

/** Recovery must retain the device identity of [rejected]. A new identity is a session failure. */
interface RecoverableAccessTokenProvider : AccessTokenProvider {
    suspend fun recover(rejected: AccessToken): AccessToken

    /** The server no longer recognizes this device. Never refresh or replay this request. */
    suspend fun deviceUnavailable(rejected: AccessToken) = Unit
}

data class SessionFailureDetails(
    val reason: String,
    val statusCode: Int? = null,
    val code: String? = null,
    val retryAtMillis: Long? = null,
    val stage: String? = null,
    val sdkCode: Int? = null,
)

class SessionAccessException(
    val details: SessionFailureDetails,
) : Exception("Device session unavailable")
