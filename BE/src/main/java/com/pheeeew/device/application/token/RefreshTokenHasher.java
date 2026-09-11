package com.pheeeew.device.application.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class RefreshTokenHasher {

    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private RefreshTokenHasher() {
    }

    static String hash(String refreshToken) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("refresh token 해시를 생성할 수 없습니다.", exception);
        }
    }

    static boolean matches(String refreshToken, String storedHash) {
        if (refreshToken == null || !isValidHash(storedHash)) {
            return false;
        }

        byte[] submitted = HexFormat.of().parseHex(hash(refreshToken));
        byte[] stored = HexFormat.of().parseHex(storedHash);

        return MessageDigest.isEqual(submitted, stored);
    }

    private static boolean isValidHash(String hash) {
        return hash != null && HASH_PATTERN.matcher(hash).matches();
    }
}
