package com.pheeeew.device.fixture;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.DeviceRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class DeviceFixture {

    private static final String 기본_시크릿 = "dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw";

    private DeviceFixture() {
    }

    public static Device.DeviceBuilder 기본_기기_빌더() {
        return Device.builder()
                .requestId(UUID.randomUUID())
                .platform(DevicePlatform.ANDROID);
    }

    public static DeviceRefreshToken.DeviceRefreshTokenBuilder 기본_리프레시_토큰_빌더() {
        UUID sessionId = UUID.randomUUID();
        return DeviceRefreshToken.builder()
                .deviceId(1L)
                .sessionId(sessionId)
                .tokenHash(토큰_해시(리프레시_토큰(sessionId)));
    }

    public static String 리프레시_토큰(UUID sessionId) {
        return sessionId + "." + 기본_시크릿;
    }

    public static String 다른_시크릿을_가진_리프레시_토큰(UUID sessionId) {
        return sessionId + "." + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    }

    public static String 토큰_해시(String value) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
