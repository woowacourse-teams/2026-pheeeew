package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.DevicePlatform

data class DeviceAttestation(
    val platform: DevicePlatform,
    val token: String? = null,
    val challenge: String? = null,
    val keyId: String? = null,
)

interface DeviceAttestationProvider {
    suspend fun create(challenge: String): DeviceAttestation
}

class NoOpDeviceAttestationProvider(
    private val platform: DevicePlatform,
) : DeviceAttestationProvider {
    override suspend fun create(challenge: String): DeviceAttestation = DeviceAttestation(platform = platform)
}
