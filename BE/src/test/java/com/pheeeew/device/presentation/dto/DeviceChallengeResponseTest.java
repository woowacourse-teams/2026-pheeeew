package com.pheeeew.device.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.application.dto.DeviceChallengeResult;
import org.junit.jupiter.api.Test;

class DeviceChallengeResponseTest {

    private static final String CHALLENGE = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM";

    @Test
    void toString_은_발급한_challenge_평문을_드러내지_않는다() {
        // given
        DeviceChallengeResponse response =
                DeviceChallengeResponse.from(DeviceChallengeResult.of(CHALLENGE, 300L));

        // when
        String 표현 = response.toString();

        // then
        assertThat(표현)
                .doesNotContain(CHALLENGE)
                .contains("expiresIn=300");
    }
}
