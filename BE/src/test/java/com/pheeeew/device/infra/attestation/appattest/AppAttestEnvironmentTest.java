package com.pheeeew.device.infra.attestation.appattest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppAttestEnvironmentTest {

    private static final int AAGUID_길이 = 16;

    @Test
    void 운영_환경은_뒤가_0으로_채워진_appattest_만_받는다() {
        // given
        byte[] 운영_aaguid = aaguid("appattest");

        // when / then
        assertThat(AppAttestEnvironment.PRODUCTION.allows(운영_aaguid)).isTrue();
        assertThat(AppAttestEnvironment.PRODUCTION.allows(aaguid("appattestdevelop"))).isFalse();
        assertThat(AppAttestEnvironment.PRODUCTION.allows(aaguid("appattestsandbox"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"appattestdevelop", "appattestsandbox"})
    void 개발_환경은_애플_문서가_갈리는_두_값을_모두_받는다(String 이름) {
        // given / when / then
        assertThat(AppAttestEnvironment.DEVELOPMENT.allows(aaguid(이름))).isTrue();
    }

    @Test
    void 개발_환경은_운영_aaguid_를_받지_않는다() {
        // given / when / then
        assertThat(AppAttestEnvironment.DEVELOPMENT.allows(aaguid("appattest"))).isFalse();
    }

    @Test
    void 뒤쪽_채움이_0이_아니게_조작된_aaguid_는_어느_환경도_받지_않는다() {
        // given
        byte[] 조작된_aaguid = aaguid("appattest");
        조작된_aaguid[AAGUID_길이 - 1] = 'X';

        // when / then
        assertThat(AppAttestEnvironment.PRODUCTION.allows(조작된_aaguid)).isFalse();
        assertThat(AppAttestEnvironment.DEVELOPMENT.allows(조작된_aaguid)).isFalse();
    }

    private static byte[] aaguid(String 이름) {
        return Arrays.copyOf(이름.getBytes(StandardCharsets.US_ASCII), AAGUID_길이);
    }
}
