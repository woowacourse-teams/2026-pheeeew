package com.pheeeew.device.application.token;

import static com.pheeeew.device.fixture.DeviceFixture.다른_시크릿을_가진_리프레시_토큰;
import static com.pheeeew.device.fixture.DeviceFixture.리프레시_토큰;
import static com.pheeeew.device.fixture.DeviceFixture.토큰_해시;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenVerifierTest {

    private static final UUID 세션_식별자 = UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b");

    private final RefreshTokenVerifier refreshTokenVerifier = new RefreshTokenVerifier();

    @Test
    void 정상_토큰에서_세션_식별자를_뽑는다() {
        // given
        String refreshToken = 리프레시_토큰(세션_식별자);

        // when / then
        assertThat(refreshTokenVerifier.extractSessionId(refreshToken)).contains(세션_식별자);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "garbage",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6bdBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk.w",
            "not-a-uuid.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw",
            "5D1AD34E-1E20-4F20-A20E-3825A095FE6B.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkww",
            " 5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw ",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.dBjftJeZ4CVPmB92K27uhbUJU1p1r+wW1gFWFOEjXkw"
    })
    void 형식이_어긋난_토큰에서는_예외_없이_빈_결과를_돌려준다(String refreshToken) {
        // given / when / then
        assertThat(refreshTokenVerifier.extractSessionId(refreshToken)).isEmpty();
    }

    @Test
    void 저장된_해시와_같은_토큰이면_일치한다() {
        // given
        String refreshToken = 리프레시_토큰(세션_식별자);

        // when / then
        assertThat(refreshTokenVerifier.matches(refreshToken, 토큰_해시(refreshToken))).isTrue();
    }

    @Test
    void 세션_식별자가_같아도_시크릿이_다르면_일치하지_않는다() {
        // given
        String storedHash = 토큰_해시(리프레시_토큰(세션_식별자));

        // when / then
        assertThat(refreshTokenVerifier.matches(다른_시크릿을_가진_리프레시_토큰(세션_식별자), storedHash)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde",
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
    })
    void 저장된_해시_형식이_어긋나면_예외_없이_불일치로_처리한다(String storedHash) {
        // given
        String refreshToken = 리프레시_토큰(세션_식별자);

        // when / then
        assertThat(refreshTokenVerifier.matches(refreshToken, storedHash)).isFalse();
    }

    @Test
    void 형식이_어긋난_토큰은_해시가_맞아도_일치하지_않는다() {
        // given
        String malformedToken = "garbage";

        // when / then
        assertThat(refreshTokenVerifier.matches(malformedToken, 토큰_해시(malformedToken))).isFalse();
    }
}
