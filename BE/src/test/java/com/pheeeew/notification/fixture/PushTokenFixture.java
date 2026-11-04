package com.pheeeew.notification.fixture;

import com.pheeeew.notification.domain.DevicePushToken;

public final class PushTokenFixture {

    public static final String 기본_등록_값 = "cA1b2C3d4E5f6G7h8I9j0K";
    public static final String 새_등록_값 = "dZ9y8X7w6V5u4T3s2R1q0P";

    private PushTokenFixture() {
    }

    public static DevicePushToken 기기_등록_값(Long deviceId, String token) {
        return DevicePushToken.builder()
                .deviceId(deviceId)
                .token(token)
                .build();
    }
}
