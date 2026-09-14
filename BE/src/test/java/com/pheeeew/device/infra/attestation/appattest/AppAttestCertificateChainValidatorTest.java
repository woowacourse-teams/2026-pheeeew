package com.pheeeew.device.infra.attestation.appattest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AppAttestCertificateChainValidatorTest {

    private static final String 루트_인증서_경로 = "/certificates/apple-app-attestation-root-ca.pem";
    private static final String 애플이_게시한_루트_지문 =
            "1cb9823ba28ba6ad2d33a006941de2ae4f513ef1d4e831b9f7e0fa7b6242c932";
    private static final String 애플이_게시한_루트_주체 =
            "ST=California, O=Apple Inc., CN=Apple App Attestation Root CA";

    @Test
    void 신뢰의_뿌리는_애플이_게시한_App_Attest_루트_CA_다() throws Exception {
        // given
        X509Certificate 루트 = 배치한_루트_인증서();

        // when
        String 지문 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(루트.getEncoded())
        );

        // then
        assertThat(지문).isEqualTo(애플이_게시한_루트_지문);
        assertThat(루트.getSubjectX500Principal().getName("RFC1779")).isEqualTo(애플이_게시한_루트_주체);
        assertThat(루트.getIssuerX500Principal()).isEqualTo(루트.getSubjectX500Principal());
        assertThat(루트.getNotAfter().toInstant()).isEqualTo(Instant.parse("2045-03-15T00:00:00Z"));
    }

    private X509Certificate 배치한_루트_인증서() throws Exception {
        try (InputStream resource = getClass().getResourceAsStream(루트_인증서_경로)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(resource);
        }
    }
}
