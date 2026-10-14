package com.pheeeew.domain.model.device

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
    AUTHENTICATION,
    UNEXPECTED,
    ATTESTATION,
    RATE_LIMITED,
    SESSION_CHANGED,
    CLOSED,
}
