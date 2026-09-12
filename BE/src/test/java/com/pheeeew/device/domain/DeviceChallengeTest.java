package com.pheeeew.device.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceChallengeTest {

    private static final String 정상_challenge = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM";

    @Test
    void 형식에_맞는_challenge_와_만료_시각으로_만든다() {
        // given
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));

        // when
        DeviceChallenge deviceChallenge = DeviceChallenge.builder()
                .challenge(정상_challenge)
                .expiresAt(expiresAt)
                .build();

        // then
        assertThat(deviceChallenge.getChallenge()).isEqualTo(정상_challenge);
        assertThat(deviceChallenge.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(deviceChallenge.getConsumedAt()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2S",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SMM",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL+reX5Us2xD2SM",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL/reX5Us2xD2SM",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZLreX5Us2xD2SM=",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL reX5Us2xD2SM",
            "PjONwTh56rDaOsphVQQeqQcPyQtLZL.reX5Us2xD2SM"
    })
    void 형식에_맞지_않는_challenge_로는_만들_수_없다(String challenge) {
        // given / when / then
        assertThatThrownBy(() -> DeviceChallenge.builder()
                .challenge(challenge)
                .expiresAt(Instant.now())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 만료_시각이_없으면_만들_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> DeviceChallenge.builder()
                .challenge(정상_challenge)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 기본_toString_은_challenge_값을_드러내지_않는다() {
        // given
        DeviceChallenge deviceChallenge = DeviceChallenge.builder()
                .challenge(정상_challenge)
                .expiresAt(Instant.now())
                .build();

        // when
        String 표현 = deviceChallenge.toString();

        // then
        assertThat(표현).doesNotContain(정상_challenge);
    }
}
