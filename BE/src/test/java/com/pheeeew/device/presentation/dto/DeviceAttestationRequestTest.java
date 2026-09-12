package com.pheeeew.device.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.DevicePlatform;
import org.junit.jupiter.api.Test;

class DeviceAttestationRequestTest {

    private static final String 무결성_토큰 = "eyJhbGciOiJBMjU2S1cifQ.encKey.iv.cipher.tag";
    private static final String CHALLENGE = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM";
    private static final String KEY_ID = "key-id-value";

    @Test
    void toString_은_무결성_토큰과_challenge_와_키_식별자를_드러내지_않는다() {
        // given
        DeviceAttestationRequest request =
                new DeviceAttestationRequest(DevicePlatform.ANDROID, 무결성_토큰, CHALLENGE, KEY_ID);

        // when
        String 표현 = request.toString();

        // then
        assertThat(표현)
                .doesNotContain(무결성_토큰)
                .doesNotContain(CHALLENGE)
                .doesNotContain(KEY_ID)
                .contains("ANDROID");
    }
}
