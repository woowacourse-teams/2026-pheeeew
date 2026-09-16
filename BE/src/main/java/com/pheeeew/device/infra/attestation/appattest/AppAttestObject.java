package com.pheeeew.device.infra.attestation.appattest;

import java.security.cert.X509Certificate;
import java.util.List;

public record AppAttestObject(String format, List<X509Certificate> certificates, byte[] authenticatorData) {

    public static AppAttestObject of(
            String format,
            List<X509Certificate> certificates,
            byte[] authenticatorData
    ) {
        return new AppAttestObject(format, certificates, authenticatorData);
    }

    public X509Certificate credentialCertificate() {
        return certificates.getFirst();
    }
}
