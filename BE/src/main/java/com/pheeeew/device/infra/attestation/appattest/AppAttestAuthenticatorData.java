package com.pheeeew.device.infra.attestation.appattest;

import java.nio.ByteBuffer;
import java.util.Arrays;

public record AppAttestAuthenticatorData(byte[] rpIdHash, long counter, byte[] aaguid, byte[] credentialId) {

    private static final int MINIMUM_LENGTH = 87;
    private static final int RP_ID_HASH_FROM = 0;
    private static final int RP_ID_HASH_TO = 32;
    private static final int COUNTER_FROM = 33;
    private static final int COUNTER_LENGTH = 4;
    private static final int AAGUID_FROM = 37;
    private static final int AAGUID_TO = 53;
    private static final int CREDENTIAL_ID_LENGTH_FROM = 53;
    private static final int CREDENTIAL_ID_LENGTH_LENGTH = 2;
    private static final int CREDENTIAL_ID_FROM = 55;
    private static final int CREDENTIAL_ID_LENGTH = 32;
    private static final int UNSIGNED_SHORT_MASK = 0xFFFF;

    public static AppAttestAuthenticatorData from(byte[] authenticatorData) {
        if (authenticatorData.length < MINIMUM_LENGTH) {
            throw new IllegalArgumentException("무결성 증명 인증 데이터가 너무 짧습니다.");
        }
        if (credentialIdLengthOf(authenticatorData) != CREDENTIAL_ID_LENGTH) {
            throw new IllegalArgumentException("무결성 증명 인증 데이터의 키 식별자 길이가 올바르지 않습니다.");
        }

        return new AppAttestAuthenticatorData(
                Arrays.copyOfRange(authenticatorData, RP_ID_HASH_FROM, RP_ID_HASH_TO),
                counterOf(authenticatorData),
                Arrays.copyOfRange(authenticatorData, AAGUID_FROM, AAGUID_TO),
                Arrays.copyOfRange(authenticatorData, CREDENTIAL_ID_FROM, CREDENTIAL_ID_FROM + CREDENTIAL_ID_LENGTH)
        );
    }

    private static int credentialIdLengthOf(byte[] authenticatorData) {
        return ByteBuffer.wrap(authenticatorData, CREDENTIAL_ID_LENGTH_FROM, CREDENTIAL_ID_LENGTH_LENGTH)
                .getShort() & UNSIGNED_SHORT_MASK;
    }

    private static long counterOf(byte[] authenticatorData) {
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(authenticatorData, COUNTER_FROM, COUNTER_LENGTH).getInt()
        );
    }
}
