package com.pheeeew.domain.model.device

import kotlinx.serialization.Serializable

/** Secret values deliberately have no generated toString. */
class DeviceAccess(
    val value: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val generation: Long,
) {
    init {
        require(value.isNotBlank())
        require(expiresAtMillis > issuedAtMillis)
    }

    fun isUsable(now: Long): Boolean {
        val margin = minOf(60_000L, (expiresAtMillis - issuedAtMillis) / 10)
        return now >= issuedAtMillis && now < expiresAtMillis - margin
    }

    override fun toString(): String = "DeviceAccess(<redacted>)"
}

enum class DevicePlatform { ANDROID, IOS }

@Serializable
class DeviceCredentials(
    val refreshToken: String? = null,
    val pendingRequestId: String? = null,
    val pendingStartedAtMillis: Long? = null,
    val generation: Long = 0,
    val preservesLegacyIdentity: Boolean = false,
) {
    init {
        require(refreshToken == null || refreshToken.isNotBlank())
        require(pendingRequestId == null || pendingRequestId.isNotBlank())
        require((pendingRequestId == null) == (pendingStartedAtMillis == null))
        require(generation >= 0)
    }

    override fun toString(): String = "DeviceCredentials(<redacted>)"
}

sealed interface DeviceSessionResult {
    class Ready(
        val access: DeviceAccess,
    ) : DeviceSessionResult

    data class Failed(
        val reason: DeviceSessionFailure,
    ) : DeviceSessionResult
}

/** Contains only diagnostics safe for UI and logging, never server response bodies. */
data class DeviceSessionFailure(
    val kind: DeviceSessionFailureKind,
    val statusCode: Int? = null,
    val code: String? = null,
    val retryAtMillis: Long? = null,
    val stage: DeviceSessionStage? = null,
    val sdkCode: Int? = null,
)

enum class DeviceSessionFailureKind {
    STORAGE,
    LEGACY_ENVIRONMENT_UNKNOWN,
    NETWORK,
    CONTRACT,
    SERVER,
    ATTESTATION,
    RATE_LIMITED,
    SESSION_CHANGED,
    CLOSED,
}
