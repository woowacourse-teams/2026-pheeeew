package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

final class E001PointSeed {

    private static final String PROTOCOL_VERSION = "E001-v1";

    private E001PointSeed() {
    }

    static long derive(
            String scenarioId,
            String originId,
            int centerI,
            int centerJ,
            long sampleSeed,
            long pointIndex
    ) {
        Objects.requireNonNull(scenarioId);
        Objects.requireNonNull(originId);

        String canonicalInput = String.join(
                "|",
                PROTOCOL_VERSION,
                scenarioId,
                originId,
                Integer.toString(centerI),
                Integer.toString(centerJ),
                Long.toString(sampleSeed),
                Long.toString(pointIndex)
        );
        byte[] digest = sha256().digest(canonicalInput.getBytes(UTF_8));

        return readBigEndianLong(digest);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없어요.", exception);
        }
    }

    private static long readBigEndianLong(byte[] bytes) {
        long result = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            result = (result << Byte.SIZE) | (bytes[index] & 0xffL);
        }
        return result;
    }
}
