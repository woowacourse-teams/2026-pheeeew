package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.DevicePlatform

/** Play Integrity 연동 전까지 서버의 선택적 attestation 흐름을 사용합니다. */
class AndroidDeviceAttestationProvider : DeviceAttestationProvider {
    override suspend fun create(challenge: String): DeviceAttestation =
        DeviceAttestation(platform = DevicePlatform.ANDROID)
}
