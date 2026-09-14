package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.fixture.AppAttestFixture.문맥_태그가_없는_리프;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_nonce;
import static com.pheeeew.device.fixture.AppAttestFixture.애플_표본_객체;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.cert.X509Certificate;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AppAttestNonceExtensionTest {

    private static final byte[] 애플이_게시한_nonce = Base64.getDecoder().decode(애플_표본_nonce);

    @Test
    void 애플_표본_리프에서_애플이_게시한_nonce_32바이트를_뽑는다() {
        // given
        X509Certificate 리프 = 애플_표본_객체().credentialCertificate();

        // when
        AppAttestNonceExtension result = AppAttestNonceExtension.from(리프);

        // then
        assertThat(result.nonce()).isEqualTo(애플이_게시한_nonce);
        assertThat(result.matches(애플이_게시한_nonce)).isTrue();
    }

    @Test
    void 다른_nonce_와는_일치하지_않는다() {
        // given
        AppAttestNonceExtension 확장 = AppAttestNonceExtension.from(애플_표본_객체().credentialCertificate());

        // when
        boolean result = 확장.matches(new byte[애플이_게시한_nonce.length]);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void 애플_설명대로_문맥_태그를_생략한_확장은_거절한다() {
        // given
        X509Certificate 문맥_태그가_빠진_리프 = 문맥_태그가_없는_리프();

        // when / then
        assertThatThrownBy(() -> AppAttestNonceExtension.from(문맥_태그가_빠진_리프))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonce_확장이_없는_인증서는_거절한다() {
        // given
        X509Certificate 중간_CA = 애플_표본_객체().certificates().get(1);

        // when / then
        assertThatThrownBy(() -> AppAttestNonceExtension.from(중간_CA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
