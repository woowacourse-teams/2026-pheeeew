package com.pheeeew.auth.infra.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pheeeew.token")
public record TokenProperties(Duration accessTtl, Duration registrationRetryWindow, Duration challengeTtl) {

    private static final Duration FIXED_ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration FIXED_REGISTRATION_RETRY_WINDOW = Duration.ofMinutes(5);
    private static final Duration FIXED_CHALLENGE_TTL = Duration.ofMinutes(5);

    public TokenProperties {
        requireFixedDuration(accessTtl, FIXED_ACCESS_TTL, "access token 만료 시간", "30분");
        requireFixedDuration(
                registrationRetryWindow,
                FIXED_REGISTRATION_RETRY_WINDOW,
                "기기 등록 재시도 창",
                "5분"
        );
        requireFixedDuration(challengeTtl, FIXED_CHALLENGE_TTL, "무결성 증명 challenge 만료 시간", "5분");
    }

    private static void requireFixedDuration(
            Duration actual,
            Duration expected,
            String settingName,
            String expectedDescription
    ) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(settingName + "은 " + expectedDescription + "이어야 합니다.");
        }
    }
}
