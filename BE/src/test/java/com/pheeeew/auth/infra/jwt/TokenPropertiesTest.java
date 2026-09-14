package com.pheeeew.auth.infra.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenPropertiesTest {

    private static final Duration 고정된_액세스_토큰_만료 = Duration.ofMinutes(30);
    private static final Duration 고정된_등록_재시도_창 = Duration.ofMinutes(5);
    private static final Duration 고정된_challenge_만료 = Duration.ofMinutes(5);

    @Test
    void 정해진_값이면_설정을_만든다() {
        // given / when
        TokenProperties tokenProperties =
                new TokenProperties(고정된_액세스_토큰_만료, 고정된_등록_재시도_창, 고정된_challenge_만료);

        // then
        assertThat(tokenProperties.accessTtl()).isEqualTo(고정된_액세스_토큰_만료);
        assertThat(tokenProperties.registrationRetryWindow()).isEqualTo(고정된_등록_재시도_창);
        assertThat(tokenProperties.challengeTtl()).isEqualTo(고정된_challenge_만료);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT29M", "PT31M", "PT30M1S", "PT0S"})
    void 액세스_토큰_만료가_30분이_아니면_설정을_만들_수_없다(Duration accessTtl) {
        // given / when / then
        assertThatThrownBy(() -> new TokenProperties(accessTtl, 고정된_등록_재시도_창, 고정된_challenge_만료))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT4M", "PT6M", "PT5M1S", "PT0S"})
    void 등록_재시도_창이_5분이_아니면_설정을_만들_수_없다(Duration registrationRetryWindow) {
        // given / when / then
        assertThatThrownBy(
                () -> new TokenProperties(고정된_액세스_토큰_만료, registrationRetryWindow, 고정된_challenge_만료)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT4M", "PT6M", "PT5M1S", "PT4M59S", "PT0S"})
    void challenge_만료가_5분이_아니면_설정을_만들_수_없다(Duration challengeTtl) {
        // given / when / then
        assertThatThrownBy(
                () -> new TokenProperties(고정된_액세스_토큰_만료, 고정된_등록_재시도_창, challengeTtl)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
