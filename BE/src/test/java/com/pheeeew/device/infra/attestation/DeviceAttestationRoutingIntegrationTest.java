package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.AppAttestFixture.설정된_app_attest_설정;
import static com.pheeeew.device.fixture.AppAttestFixture.정품_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.증명에_묶인_challenge;
import static com.pheeeew.device.fixture.AppAttestFixture.키_식별자;
import static com.pheeeew.device.fixture.AppAttestFixture.합성_루트를_신뢰하는_체인_검증기;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.JWE_무결성_토큰;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.자격증명이_있는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.정품_복호화_응답;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.DeviceAttestationBudgetService;
import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.application.dto.DeviceChallengeResult;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.application.token.RefreshTokenIssuer;
import com.pheeeew.device.domain.DeviceChallenge;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceAttestationBudgetRepository;
import com.pheeeew.device.domain.repository.DeviceChallengeRepository;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import com.pheeeew.device.infra.attestation.appattest.AppAttestDeviceAttestationVerifier;
import com.pheeeew.device.infra.attestation.appattest.AppAttestMetrics;
import com.pheeeew.device.infra.attestation.appattest.AppAttestObjectDecoder;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceAttestationRoutingIntegrationTest {

    private static final String App_Attest_승인_지표 = "pheeeew.device.appattest.accepted";
    private static final String App_Attest_거절_지표 = "pheeeew.device.appattest.rejected";
    private static final String Play_Integrity_승인_지표 = "pheeeew.device.attestation.accepted";
    private static final String Play_Integrity_거절_지표 = "pheeeew.device.attestation.rejected";

    @Autowired
    private DeviceChallengeService deviceChallengeService;

    @Autowired
    private DeviceChallengeRepository deviceChallengeRepository;

    @Autowired
    private DeviceAttestationBudgetService deviceAttestationBudgetService;

    @Autowired
    private DeviceAttestationBudgetRepository deviceAttestationBudgetRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private TokenProperties tokenProperties;

    private FakeGoogleApiServer 가짜_구글;
    private SimpleMeterRegistry registry;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        가짜_구글 = FakeGoogleApiServer.시작한다();
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.access", 3599));
        registry = new SimpleMeterRegistry();
        deviceService = 라우터를_낀_등록_서비스를_만든다();
        challenge_를_저장한다(증명에_묶인_challenge);
    }

    @AfterEach
    void tearDown() {
        가짜_구글.close();
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        deviceChallengeRepository.deleteAllInBatch();
        deviceAttestationBudgetRepository.deleteAllInBatch();
    }

    @Test
    void 안드로이드_증명은_Play_Integrity_로만_가고_App_Attest_지표는_움직이지_않는다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));

        // when
        DeviceSaveResult result = deviceService.save(
                UUID.randomUUID(),
                DeviceAttestation.of(DevicePlatform.ANDROID, JWE_무결성_토큰, 발급.challenge(), null)
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(가짜_구글.복호화_요청_수()).isOne();
        assertThat(카운터(Play_Integrity_승인_지표)).isOne();
        assertThat(카운터(App_Attest_승인_지표)).isZero();
        assertThat(App_Attest_거절_수("PROPERTIES_MISSING")).isZero();
    }

    @Test
    void IOS_증명은_App_Attest_로만_가고_구글을_한_번도_부르지_않는다() {
        // given
        DeviceAttestation ios_증명 =
                DeviceAttestation.of(DevicePlatform.IOS, 정품_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        DeviceSaveResult result = deviceService.save(UUID.randomUUID(), ios_증명);

        // then
        assertThat(result.created()).isTrue();
        assertThat(구글_호출_수()).isZero();
        assertThat(카운터(App_Attest_승인_지표)).isOne();
        assertThat(카운터(Play_Integrity_승인_지표)).isZero();
    }

    @Test
    void IOS_증명은_Play_Integrity_로_검증되지_않는다() {
        // given
        DeviceAttestation Play_Integrity_토큰을_실은_IOS_증명 =
                DeviceAttestation.of(DevicePlatform.IOS, JWE_무결성_토큰, 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(
                () -> deviceService.save(UUID.randomUUID(), Play_Integrity_토큰을_실은_IOS_증명)
        );

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(구글_호출_수()).isZero();
        assertThat(App_Attest_거절_수("MALFORMED_TOKEN")).isOne();
        assertThat(Play_Integrity_거절_수("MALFORMED_TOKEN")).isZero();
        assertThat(Play_Integrity_거절_수("CHALLENGE_UNUSABLE")).isZero();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void IOS_경로는_검증을_끝까지_마쳐도_구글_예산과_challenge_시도_수를_건드리지_않는다() {
        // given
        DeviceAttestation ios_증명 =
                DeviceAttestation.of(DevicePlatform.IOS, 정품_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        DeviceSaveResult result = deviceService.save(UUID.randomUUID(), ios_증명);

        // then
        assertThat(result.created()).isTrue();
        assertThat(카운터(App_Attest_승인_지표)).isOne();
        assertThat(deviceAttestationBudgetRepository.count()).isZero();
        assertThat(시도_수(증명에_묶인_challenge)).isZero();
        assertThat(구글_호출_수()).isZero();
    }

    private DeviceService 라우터를_낀_등록_서비스를_만든다() {
        PlayIntegrityProperties playIntegrityProperties = 자격증명이_있는_설정(가짜_구글.토큰_엔드포인트());
        PlayIntegrityDeviceAttestationVerifier playIntegrityVerifier = new PlayIntegrityDeviceAttestationVerifier(
                new PlayIntegrityTokenDecoder(
                        가짜_구글.이_서버를_향하는_클라이언트(),
                        new GoogleAccessTokenProvider(RestClient.builder().build(), playIntegrityProperties),
                        playIntegrityProperties
                ),
                deviceChallengeService,
                deviceAttestationBudgetService,
                playIntegrityProperties,
                new PlayIntegrityMetrics(registry)
        );
        AppAttestDeviceAttestationVerifier appAttestVerifier = new AppAttestDeviceAttestationVerifier(
                new AppAttestObjectDecoder(),
                합성_루트를_신뢰하는_체인_검증기(),
                deviceChallengeService,
                설정된_app_attest_설정(),
                new AppAttestMetrics(registry)
        );

        return new DeviceService(
                deviceRepository,
                refreshTokenIssuer,
                accessTokenIssuer,
                new RoutingDeviceAttestationVerifier(playIntegrityVerifier, appAttestVerifier),
                tokenProperties
        );
    }

    private void challenge_를_저장한다(String challenge) {
        deviceChallengeRepository.save(DeviceChallenge.builder()
                .challenge(challenge)
                .expiresAt(Instant.now().plus(tokenProperties.challengeTtl()))
                .build());
    }

    private long 구글_호출_수() {
        return 가짜_구글.토큰_요청_수() + 가짜_구글.복호화_요청_수();
    }

    private int 시도_수(String challenge) {
        return deviceChallengeRepository.findAll().stream()
                .filter(deviceChallenge -> deviceChallenge.getChallenge().equals(challenge))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("challenge 행이 없습니다."))
                .getAttemptCount();
    }

    private double 카운터(String name) {
        Counter counter = registry.find(name).counter();

        return counter == null ? 0D : counter.count();
    }

    private double App_Attest_거절_수(String reason) {
        return 거절_수(App_Attest_거절_지표, reason);
    }

    private double Play_Integrity_거절_수(String reason) {
        return 거절_수(Play_Integrity_거절_지표, reason);
    }

    private double 거절_수(String name, String reason) {
        Counter counter = registry.find(name).tag("reason", reason).counter();

        return counter == null ? 0D : counter.count();
    }

    private void 증명을_확인할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_INVALID);
    }
}
