package com.pheeeew.core.di.device

import com.pheeeew.data.local.device.LegacyCredentialPolicy
import com.pheeeew.data.remote.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.attestation.DeviceProofProvider

/** Build settings are checked again before creating a client or a platform proof provider. */
data class DeviceSessionBuildConfig(
    val isDebug: Boolean,
    val environment: String,
    val baseUrl: String,
    val attestationMode: String,
    val version: String,
    val pressInputMode: PressInputMode = PressInputMode.HTTP,
    val pressRaceReceiveMode: PressRaceReceiveMode = PressRaceReceiveMode.WEBSOCKET,
) {
    init {
        require(pressInputMode == PressInputMode.HTTP) {
            "Press receiver comparisons require the existing HTTP press input path"
        }
        require(environment == if (isDebug) "dev" else "prod") { "Device environment does not match build type" }
        require(baseUrl == if (isDebug) DEV_URL else PROD_URL) { "Device API URL does not match build type" }
        require(attestationMode in setOf("platform_only", "required")) { "Unknown device attestation mode" }
        require(isDebug || attestationMode == "required") { "Release requires device attestation" }
        require(isDebug || pressRaceReceiveMode == PressRaceReceiveMode.WEBSOCKET) {
            "Release press ranking reception is fixed to WebSocket"
        }
        require(version.matches(Regex("[A-Za-z0-9._+-]{1,64}"))) { "Invalid app version" }
    }

    // Published legacy Release builds used PROD_URL. Debug gets an isolated dev session without touching old credentials.
    val legacyCredentialPolicy: LegacyCredentialPolicy
        get() =
            if (isDebug) {
                LegacyCredentialPolicy.START_NEW_ENVIRONMENT_SESSION
            } else {
                LegacyCredentialPolicy.IMPORT_CURRENT_ENVIRONMENT
            }

    fun policy(createProofProvider: () -> DeviceProofProvider): DeviceAttestationPolicy =
        if (attestationMode == "platform_only") {
            DeviceAttestationPolicy.PlatformOnly
        } else {
            DeviceAttestationPolicy.Required(createProofProvider())
        }

    companion object {
        const val DEV_URL = "https://api-dev.pheeeew.com"
        const val PROD_URL = "https://api.pheeeew.com"
    }
}

/** Selected once when the app graph is created; there is no runtime fallback. */
enum class PressInputMode {
    HTTP,
    WEBSOCKET_DEVELOPMENT,
    ;

    companion object {
        fun fromBuildValue(value: String): PressInputMode =
            when (value) {
                "http" -> HTTP
                "ws" -> WEBSOCKET_DEVELOPMENT
                else -> error("Unknown press input build mode")
            }
    }
}

/** Debug-only ranking receiver experiment; Release remains on the existing WS ranking stream. */
enum class PressRaceReceiveMode {
    WEBSOCKET,
    CACHE_POLLING,
    SSE,
    BASIC_POLLING,
    ;

    companion object {
        fun fromBuildValue(value: String): PressRaceReceiveMode =
            when (value) {
                "ws" -> WEBSOCKET
                "cache_polling" -> CACHE_POLLING
                "sse" -> SSE
                "basic_polling" -> BASIC_POLLING
                else -> error("Unknown press ranking receive build mode")
            }
    }
}
