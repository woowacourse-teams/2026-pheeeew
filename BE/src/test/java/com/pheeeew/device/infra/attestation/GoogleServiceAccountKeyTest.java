package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_이메일;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleServiceAccountKeyTest {

    private static final String 토큰_주소 = "https://oauth2.googleapis.com/token";

    @Test
    void 토큰_주소가_없으면_구글_기본_주소를_쓴다() {
        // given
        String serviceAccountBase64 = 토큰_주소를_지운_설정();

        // when
        GoogleServiceAccountKey key = GoogleServiceAccountKey.from(serviceAccountBase64);

        // then
        assertThat(key.tokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://oauth2.googleapis.com/token",
            "http://127.0.0.1:1234/token",
            "http://127.0.0.1/token",
            "http://127.255.255.255/token",
            "http://[::1]:1234/token"
    })
    void https_와_루프백_리터럴_http_토큰_주소는_원문_그대로_쓴다(String tokenUri) {
        // given
        String serviceAccountBase64 = 서비스_계정_설정(tokenUri);

        // when
        GoogleServiceAccountKey key = GoogleServiceAccountKey.from(serviceAccountBase64);

        // then
        assertThat(key.tokenUri()).isEqualTo(tokenUri);
        assertThat(key.clientEmail()).isEqualTo(서비스_계정_이메일);
        assertThat(key.privateKey().getAlgorithm()).isEqualTo("RSA");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.example.com/token",
            "http://localhost:1234",
            "http://LOCALHOST/token",
            "http://127.0.0.1.evil.example.com/token",
            "http://0.0.0.0/token",
            "http://10.0.0.1/token",
            "http://[::2]/token",
            "http://127.0.0.256/token"
    })
    void 이름이나_루프백이_아닌_주소로_평문_토큰_교환을_하려_하면_설정_오류로_올린다(String tokenUri) {
        // given
        String serviceAccountBase64 = 서비스_계정_설정(tokenUri);

        // when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Play Integrity 서비스 계정 token_uri 설정이 올바르지 않습니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://attacker:secret@oauth2.googleapis.com/token",
            "http://attacker@127.0.0.1:1234/token"
    })
    void userinfo_가_붙은_토큰_주소는_설정_오류로_올린다(String tokenUri) {
        // given
        String serviceAccountBase64 = 서비스_계정_설정(tokenUri);

        // when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Play Integrity 서비스 계정 token_uri 설정이 올바르지 않습니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/token",
            "oauth2.googleapis.com/token",
            "mailto:tokens@evil.example.com",
            "file:///etc/passwd",
            "ftp://oauth2.googleapis.com/token",
            "ws://127.0.0.1:1234/token",
            "https://"
    })
    void 계층적_절대_URI_가_아니거나_전송이_허용되지_않은_토큰_주소는_설정_오류로_올린다(String tokenUri) {
        // given
        String serviceAccountBase64 = 서비스_계정_설정(tokenUri);

        // when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Play Integrity 서비스 계정 token_uri 설정이 올바르지 않습니다.");
    }

    @Test
    void 개인키가_PKCS8_PEM_이_아니면_원문을_노출하지_않고_설정_오류로_올린다() {
        // given
        String serviceAccountBase64 = 인코딩한다("""
                {"client_email": "a@b.iam.gserviceaccount.com", "private_key": "super-secret-key-material"}
                """);

        // when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Play Integrity 서비스 계정 개인 키 설정이 올바르지 않습니다.")
                .hasMessageNotContaining("super-secret-key-material")
                .hasMessageNotContaining(serviceAccountBase64);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-base64!!",
            "   "
    })
    void base64_가_아니면_설정_오류로_올린다(String serviceAccountBase64) {
        // given / when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"client_email\": \"a@b.com\"}",
            "{\"private_key\": \"x\"}",
            "{\"client_email\": \"   \", \"private_key\": \"x\"}"
    })
    void 필수_필드가_없으면_설정_오류로_올린다(String serviceAccountJson) {
        // given
        String serviceAccountBase64 = 인코딩한다(serviceAccountJson);

        // given / when / then
        assertThatThrownBy(() -> GoogleServiceAccountKey.from(serviceAccountBase64))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toString_은_이메일과_개인키를_드러내지_않는다() {
        // given
        GoogleServiceAccountKey key = GoogleServiceAccountKey.from(서비스_계정_설정(토큰_주소));

        // when
        String 표현 = key.toString();

        // then
        assertThat(표현)
                .doesNotContain(서비스_계정_이메일)
                .doesNotContain(key.privateKey().toString())
                .contains(토큰_주소);
    }

    private String 토큰_주소를_지운_설정() {
        String serviceAccountJson = new String(Base64.getDecoder().decode(서비스_계정_설정(토큰_주소)));
        return 인코딩한다(serviceAccountJson.replaceAll("\\s*\"token_uri\":.*\\n", ""));
    }

    private String 인코딩한다(String serviceAccountJson) {
        return Base64.getEncoder().encodeToString(serviceAccountJson.getBytes());
    }
}
