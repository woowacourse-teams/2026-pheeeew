package com.pheeeew.data.remote.attestation

/** Selected explicitly by the composition root. Proof failures must never downgrade this policy. */
sealed interface DeviceAttestationPolicy {
    data object PlatformOnly : DeviceAttestationPolicy

    class Required(
        val provider: DeviceProofProvider,
    ) : DeviceAttestationPolicy
}
