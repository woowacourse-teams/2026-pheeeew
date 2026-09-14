package com.pheeeew.device.infra.attestation.appattest;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public enum AppAttestEnvironment {

    DEVELOPMENT(Set.of("appattestdevelop", "appattestsandbox")),
    PRODUCTION(Set.of("appattest"));

    private final Set<String> allowedAaguids;

    AppAttestEnvironment(Set<String> allowedAaguids) {
        this.allowedAaguids = allowedAaguids;
    }

    public boolean allows(byte[] aaguid) {
        return allowedAaguids.contains(withoutTrailingPadding(aaguid));
    }

    private static String withoutTrailingPadding(byte[] aaguid) {
        int end = aaguid.length;
        while (end > 0 && aaguid[end - 1] == 0) {
            end--;
        }
        return new String(aaguid, 0, end, StandardCharsets.ISO_8859_1);
    }
}
