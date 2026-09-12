package com.pheeeew.auth.infra.jwt;

import static com.pheeeew.auth.fixture.JwtTestKeys.기본_키_설정;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

    @Test
    void 문자열_표현에_키_값이_들어가지_않는다() {
        // given
        JwtProperties jwtProperties = 기본_키_설정();

        // when
        String printed = jwtProperties.toString();

        // then
        assertThat(printed)
                .doesNotContain(jwtProperties.privateKeyBase64())
                .doesNotContain(jwtProperties.publicKeyBase64())
                .contains("<redacted>");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void 개인_키_설정이_비어_있으면_만들_수_없다(String privateKeyBase64) {
        // given / when / then
        assertThatThrownBy(() -> new JwtProperties(privateKeyBase64, 기본_키_설정().publicKeyBase64()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void 공개_키_설정이_비어_있으면_만들_수_없다(String publicKeyBase64) {
        // given / when / then
        assertThatThrownBy(() -> new JwtProperties(기본_키_설정().privateKeyBase64(), publicKeyBase64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 개인_키_파싱_실패_메시지에_키_값과_원인_예외가_남지_않는다() {
        // given
        String invalidPrivateKey = "bm90LWEtcHJpdmF0ZS1rZXk=";
        JwtProperties jwtProperties = new JwtProperties(invalidPrivateKey, 기본_키_설정().publicKeyBase64());

        // when
        Throwable throwable = catchThrowable(jwtProperties::privateKey);

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT 개인 키 설정이 올바르지 않습니다.")
                .hasNoCause();
        assertThat(throwable.getMessage()).doesNotContain(invalidPrivateKey);
    }

    @Test
    void 공개_키_파싱_실패_메시지에_키_값과_원인_예외가_남지_않는다() {
        // given
        String invalidPublicKey = "bm90LWEtcHVibGljLWtleQ==";
        JwtProperties jwtProperties = new JwtProperties(기본_키_설정().privateKeyBase64(), invalidPublicKey);

        // when
        Throwable throwable = catchThrowable(jwtProperties::publicKey);

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT 공개 키 설정이 올바르지 않습니다.")
                .hasNoCause();
        assertThat(throwable.getMessage()).doesNotContain(invalidPublicKey);
    }
}
