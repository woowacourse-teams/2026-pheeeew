package com.pheeeew.device.infra.attestation;

import com.pheeeew.device.application.DeviceAttestationVerifier;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.infra.attestation.appattest.AppAttestDeviceAttestationVerifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class RoutingDeviceAttestationVerifier implements DeviceAttestationVerifier {

    private final PlayIntegrityDeviceAttestationVerifier playIntegrityDeviceAttestationVerifier;
    private final AppAttestDeviceAttestationVerifier appAttestDeviceAttestationVerifier;

    public RoutingDeviceAttestationVerifier(
            PlayIntegrityDeviceAttestationVerifier playIntegrityDeviceAttestationVerifier,
            AppAttestDeviceAttestationVerifier appAttestDeviceAttestationVerifier
    ) {
        this.playIntegrityDeviceAttestationVerifier = playIntegrityDeviceAttestationVerifier;
        this.appAttestDeviceAttestationVerifier = appAttestDeviceAttestationVerifier;
    }

    @Override
    public void verify(DeviceAttestation attestation) {
        DeviceAttestationVerifier verifier = switch (attestation.platform()) {
            case ANDROID -> playIntegrityDeviceAttestationVerifier;
            case IOS -> appAttestDeviceAttestationVerifier;
        };

        verifier.verify(attestation);
    }
}
