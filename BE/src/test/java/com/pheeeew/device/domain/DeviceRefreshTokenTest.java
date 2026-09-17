package com.pheeeew.device.domain;

import static com.pheeeew.device.fixture.DeviceFixture.기본_리프레시_토큰_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceRefreshTokenTest {

    private static final String 유효한_해시 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            "0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef",
            "0123456789abcdeg0123456789abcdef0123456789abcdef0123456789abcdef",
            ""
    })
    void 해시가_64자_16진_소문자가_아니면_토큰을_만들_수_없다(String tokenHash) {
        // given / when / then
        assertThatThrownBy(() -> DeviceRefreshToken.builder()
                .deviceId(1L)
                .sessionId(UUID.randomUUID())
                .tokenHash(tokenHash)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 해시가_64자_16진_소문자이면_토큰을_만든다() {
        // given / when
        DeviceRefreshToken token = DeviceRefreshToken.builder()
                .deviceId(1L)
                .sessionId(UUID.randomUUID())
                .tokenHash(유효한_해시)
                .build();

        // then
        assertThat(token.getTokenHash()).isEqualTo(유효한_해시);
    }

    @Test
    void 폐기하지_않았고_만료가_없으면_사용할_수_있다() {
        // given
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();

        // when / then
        assertThat(token.isUsable(Instant.now())).isTrue();
    }

    @Test
    void 폐기한_토큰은_사용할_수_없다() {
        // given
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();

        // when
        token.revoke();

        // then
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void 두_번_폐기해도_폐기_시각이_밀리지_않는다() {
        // given
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();
        token.revoke();
        Instant firstRevokedAt = token.getRevokedAt();

        // when
        token.revoke();

        // then
        assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void 만료_시각이_지난_토큰은_사용할_수_없다() {
        // given
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();
        ReflectionTestUtils.setField(token, "expiresAt", now.minusSeconds(1));

        // when / then
        assertThat(token.isUsable(now)).isFalse();
    }

    @Test
    void 만료_시각이_남은_토큰은_사용할_수_있다() {
        // given
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();
        ReflectionTestUtils.setField(token, "expiresAt", now.plusSeconds(1));

        // when / then
        assertThat(token.isUsable(now)).isTrue();
    }

    @Test
    void 만료되지_않았어도_폐기했으면_사용할_수_없다() {
        // given
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        DeviceRefreshToken token = 기본_리프레시_토큰_빌더().build();
        ReflectionTestUtils.setField(token, "expiresAt", now.plusSeconds(3600));
        token.revoke();

        // when / then
        assertThat(token.isUsable(now)).isFalse();
    }
}
