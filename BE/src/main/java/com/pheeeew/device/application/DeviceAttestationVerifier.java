package com.pheeeew.device.application;

import com.pheeeew.device.application.dto.DeviceAttestation;

public interface DeviceAttestationVerifier {

    void verify(DeviceAttestation attestation);
}
