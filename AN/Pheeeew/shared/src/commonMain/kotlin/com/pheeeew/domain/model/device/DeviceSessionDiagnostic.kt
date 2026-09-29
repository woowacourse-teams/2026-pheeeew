package com.pheeeew.domain.model.device

enum class DeviceSessionStage { STORAGE, CHALLENGE, PROOF, REGISTER, REFRESH, AUTHENTICATED_REQUEST }

enum class DeviceDiagnosticOutcome { STARTED, SUCCEEDED, FAILED }

/** Only allowlisted fields cross this boundary. No Throwable, request, response or credential payloads. */
class DeviceSessionDiagnostic(
    val stage: DeviceSessionStage,
    val outcome: DeviceDiagnosticOutcome,
    val statusCode: Int? = null,
    serverCode: String? = null,
    val sdkCode: Int? = null,
    val failureKind: DeviceSessionFailureKind? = null,
) {
    val serverCode = serverCode?.takeIf { it.matches(Regex("(?:DEVICE|AUTH|COMMON)-[0-9]{3}")) }

    override fun toString(): String =
        "stage=$stage outcome=$outcome status=$statusCode serverCode=$serverCode sdkCode=$sdkCode failure=$failureKind"
}

fun interface DeviceSessionDiagnostics {
    fun record(event: DeviceSessionDiagnostic)
}

/** A logging failure cannot turn successful credential storage into a registration failure. */
fun DeviceSessionDiagnostics.recordSafely(event: DeviceSessionDiagnostic) {
    try {
        record(event)
    } catch (_: Exception) {
        // Diagnostics are best-effort and never participate in the session transaction.
    }
}
