package com.pheeeew.domain.model.device

import kotlinx.serialization.Serializable

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
