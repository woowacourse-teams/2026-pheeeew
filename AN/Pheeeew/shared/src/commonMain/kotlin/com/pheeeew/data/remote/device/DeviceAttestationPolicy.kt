package com.pheeeew.data.remote.device

import com.pheeeew.domain.model.device.DevicePlatform

/** Selected explicitly by the composition root. Proof failures must never downgrade this policy. */
sealed interface DeviceAttestationPolicy {
    data object PlatformOnly : DeviceAttestationPolicy

    class Required(
        val provider: DeviceProofProvider,
    ) : DeviceAttestationPolicy
}

fun interface DeviceProofProvider {
    suspend fun attest(
        platform: DevicePlatform,
        challenge: String,
    ): DeviceAttestationDto
}

/** Drops platform exception messages and causes, which may contain private SDK data. */
open class DeviceProofException(
    val sdkCode: Int? = null,
) : Exception("Device proof generation failed")
