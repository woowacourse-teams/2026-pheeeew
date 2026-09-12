package com.pheeeew.device.infra.attestation;

import java.util.List;

public record PlayIntegrityPayload(
        String requestPackageName,
        String challenge,
        String appRecognitionVerdict,
        List<String> deviceRecognitionVerdicts
) {

    public static PlayIntegrityPayload of(
            String requestPackageName,
            String challenge,
            String appRecognitionVerdict,
            List<String> deviceRecognitionVerdicts
    ) {
        return new PlayIntegrityPayload(
                requestPackageName,
                challenge,
                appRecognitionVerdict,
                List.copyOf(deviceRecognitionVerdicts)
        );
    }

    @Override
    public String toString() {
        return "PlayIntegrityPayload[<redacted>]";
    }
}
