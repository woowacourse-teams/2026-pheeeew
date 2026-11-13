package com.pheeeew.data.remote.attestation

import com.pheeeew.data.remote.dto.DeviceAttestationDto
import com.pheeeew.domain.model.device.DevicePlatform

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
