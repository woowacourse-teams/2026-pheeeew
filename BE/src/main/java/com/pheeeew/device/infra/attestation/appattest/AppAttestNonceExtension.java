package com.pheeeew.device.infra.attestation.appattest;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public record AppAttestNonceExtension(byte[] nonce) {

    private static final String NONCE_EXTENSION_OID = "1.2.840.113635.100.8.2";
    private static final byte[] NONCE_EXTENSION_PREFIX = {
            0x04, 0x26, 0x30, 0x24, (byte) 0xA1, 0x22, 0x04, 0x20
    };
    private static final int NONCE_LENGTH = 32;
    private static final int EXTENSION_LENGTH = NONCE_EXTENSION_PREFIX.length + NONCE_LENGTH;

    public static AppAttestNonceExtension from(X509Certificate credentialCertificate) {
        byte[] extensionValue = credentialCertificate.getExtensionValue(NONCE_EXTENSION_OID);
        if (extensionValue == null || extensionValue.length != EXTENSION_LENGTH) {
            throw new IllegalArgumentException("무결성 증명 인증서에 nonce 확장이 없습니다.");
        }
        if (!Arrays.equals(
                extensionValue, 0, NONCE_EXTENSION_PREFIX.length,
                NONCE_EXTENSION_PREFIX, 0, NONCE_EXTENSION_PREFIX.length
        )) {
            throw new IllegalArgumentException("무결성 증명 인증서의 nonce 확장 형식이 올바르지 않습니다.");
        }

        return new AppAttestNonceExtension(
                Arrays.copyOfRange(extensionValue, NONCE_EXTENSION_PREFIX.length, EXTENSION_LENGTH)
        );
    }

    public boolean matches(byte[] expectedNonce) {
        return MessageDigest.isEqual(nonce, expectedNonce);
    }
}
