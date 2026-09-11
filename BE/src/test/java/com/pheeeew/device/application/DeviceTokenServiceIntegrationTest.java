package com.pheeeew.device.application;

import static com.pheeeew.device.fixture.DeviceFixture.다른_시크릿을_가진_리프레시_토큰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.DeviceRefreshToken;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceTokenServiceIntegrationTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @AfterEach
    void tearDown() {
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 유효한_리프레시_토큰으로_그_기기의_액세스_토큰을_재발급한다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult saved = deviceService.save(requestId, DevicePlatform.ANDROID);
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        AccessTokenResult result = deviceTokenService.reissueAccessToken(saved.refreshToken());

        // then
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(AccessTokenClaims.from(jwtDecoder.decode(result.accessToken())).devicePublicId())
                .isEqualTo(device.getPublicId());
    }

    @Test
    void 같은_리프레시_토큰을_여러_번_써도_회전되지_않는다() {
        // given
        DeviceSaveResult saved = deviceService.save(UUID.randomUUID(), DevicePlatform.ANDROID);

        // when
        deviceTokenService.reissueAccessToken(saved.refreshToken());
        deviceTokenService.reissueAccessToken(saved.refreshToken());
        deviceTokenService.reissueAccessToken(saved.refreshToken());

        // then
        assertThat(deviceRefreshTokenRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.findAll().getFirst().getRevokedAt()).isNull();
    }

    @Test
    void 폐기된_리프레시_토큰으로는_재발급할_수_없다() {
        // given
        DeviceSaveResult saved = deviceService.save(UUID.randomUUID(), DevicePlatform.ANDROID);
        폐기한다(saved.refreshToken());

        // when
        Throwable throwable = catchThrowable(() -> deviceTokenService.reissueAccessToken(saved.refreshToken()));

        // then
        인증_정보를_사용할_수_없다(throwable);
    }

    @Test
    void 존재하지_않는_세션의_리프레시_토큰으로는_재발급할_수_없다() {
        // given
        deviceService.save(UUID.randomUUID(), DevicePlatform.ANDROID);
        String unknownSessionToken = 다른_시크릿을_가진_리프레시_토큰(UUID.randomUUID());

        // when
        Throwable throwable = catchThrowable(() -> deviceTokenService.reissueAccessToken(unknownSessionToken));

        // then
        인증_정보를_사용할_수_없다(throwable);
    }

    @Test
    void 세션은_같지만_시크릿을_바꾼_토큰으로는_재발급할_수_없다() {
        // given
        DeviceSaveResult saved = deviceService.save(UUID.randomUUID(), DevicePlatform.ANDROID);
        UUID sessionId = deviceRefreshTokenRepository.findAll().getFirst().getSessionId();
        String forged = 다른_시크릿을_가진_리프레시_토큰(sessionId);

        // when
        Throwable throwable = catchThrowable(() -> deviceTokenService.reissueAccessToken(forged));

        // then
        assertThat(forged).isNotEqualTo(saved.refreshToken());
        인증_정보를_사용할_수_없다(throwable);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "garbage",
            "not-a-uuid.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXkw",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
            "5d1ad34e-1e20-4f20-a20e-3825a095fe6b.short",
            "../../etc/passwd"
    })
    void 형식이_어긋난_리프레시_토큰은_서버_오류가_아니라_인증_실패로_처리한다(String refreshToken) {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceTokenService.reissueAccessToken(refreshToken));

        // then
        인증_정보를_사용할_수_없다(throwable);
    }

    @Test
    void 재발급_실패_메시지에_제출된_토큰이_들어가지_않는다() {
        // given
        String forged = 다른_시크릿을_가진_리프레시_토큰(UUID.randomUUID());

        // when
        Throwable throwable = catchThrowable(() -> deviceTokenService.reissueAccessToken(forged));

        // then
        assertThat(throwable).hasMessage("인증 정보를 사용할 수 없습니다.");
        assertThat(throwable.getMessage()).doesNotContain(forged);
    }

    private void 폐기한다(String refreshToken) {
        UUID sessionId = UUID.fromString(refreshToken.split("\\.")[0]);
        DeviceRefreshToken storedToken = deviceRefreshTokenRepository.findBySessionId(sessionId).orElseThrow();
        storedToken.revoke();
        deviceRefreshTokenRepository.saveAndFlush(storedToken);
    }

    private void 인증_정보를_사용할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_REFRESH_TOKEN_INVALID);
    }
}
