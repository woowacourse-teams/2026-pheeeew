package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_authData_바이트_수;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_바이트_수;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_리프_바이트_수;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_중간_CA_바이트_수;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_증명;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppAttestObjectDecoderTest {

    private static final int 상한_길이 = 32_768;

    private final AppAttestObjectDecoder decoder = new AppAttestObjectDecoder();

    @Test
    void 애플이_게시한_실제_증명_객체를_그대로_해석한다() throws Exception {
        // given
        byte[] attestationObject = decoder.decodeToken(애플_표본_증명());

        // when
        AppAttestObject result = decoder.decode(attestationObject);

        // then
        assertThat(attestationObject).hasSize(애플_표본_바이트_수);
        assertThat(result.format()).isEqualTo("apple-appattest");
        assertThat(result.certificates()).hasSize(2);
        assertThat(result.credentialCertificate().getEncoded()).hasSize(애플_표본_리프_바이트_수);
        assertThat(result.certificates().get(1).getEncoded()).hasSize(애플_표본_중간_CA_바이트_수);
        assertThat(result.authenticatorData()).hasSize(애플_표본_authData_바이트_수);
    }

    @Test
    void 상한을_넘는_토큰은_디코딩조차_하지_않는다() {
        // given
        String 상한을_넘는_토큰 = "A".repeat(상한_길이 + 1);

        // when / then
        assertThatThrownBy(() -> decoder.decodeToken(상한을_넘는_토큰))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"증명이 아닙니다", "not base64!!", "AAAAA", "AAAA="})
    void 표준_base64_가_아닌_토큰은_거절한다(String 토큰) {
        // given / when / then
        assertThatThrownBy(() -> decoder.decodeToken(토큰))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void CBOR_맵이_아닌_바이트는_거절한다() {
        // given
        byte[] CBOR_이_아닌_바이트 = "this is definitely not a cbor map".getBytes(StandardCharsets.US_ASCII);

        // when / then
        assertThatThrownBy(() -> decoder.decode(CBOR_이_아닌_바이트))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
