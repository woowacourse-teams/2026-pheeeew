package com.pheeeew.core.di.device

import com.pheeeew.data.local.device.LegacyCredentialPolicy
import com.pheeeew.data.remote.device.attestation.DeviceAttestationPolicy
import com.pheeeew.data.remote.device.attestation.DeviceProofProvider

/** Build settings are checked again before creating a client or a platform proof provider. */
data class DeviceSessionBuildConfig(
    val isDebug: Boolean,
    val environment: String,
    val baseUrl: String,
    val attestationMode: String,
    val version: String,
) {
    init {
        require(environment == if (isDebug) "dev" else "prod") { "Device environment does not match build type" }
        require(baseUrl == if (isDebug) DEV_URL else PROD_URL) { "Device API URL does not match build type" }
        require(attestationMode in setOf("platform_only", "required")) { "Unknown device attestation mode" }
        require(isDebug || attestationMode == "required") { "Release requires device attestation" }
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
