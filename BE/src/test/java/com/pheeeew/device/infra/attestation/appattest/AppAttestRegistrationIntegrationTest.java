package com.pheeeew.device.infra.attestation.appattest;

import static com.pheeeew.device.fixture.AppAttestFixture.DER_공개키_정보를_해시한_키_식별자;
import static com.pheeeew.device.fixture.AppAttestFixture.authData가_86바이트인_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.강제가_켜진_설정;
import static com.pheeeew.device.fixture.AppAttestFixture.개발_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.개발_환경_설정;
import static com.pheeeew.device.fixture.AppAttestFixture.다른_앱으로_만든_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.샌드박스_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.설정된_app_attest_설정;
import static com.pheeeew.device.fixture.AppAttestFixture.설정이_없는_app_attest_설정;
import static com.pheeeew.device.fixture.AppAttestFixture.정품_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.증명에_묶이지_않은_challenge;
import static com.pheeeew.device.fixture.AppAttestFixture.증명에_묶인_challenge;
import static com.pheeeew.device.fixture.AppAttestFixture.카운터가_1인_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.키_식별자;
import static com.pheeeew.device.fixture.AppAttestFixture.키_식별자가_어긋난_증명;
import static com.pheeeew.device.fixture.AppAttestFixture.합성_루트를_신뢰하는_체인_검증기;
import static com.pheeeew.device.fixture.AppAttestFixture.형식이_다른_증명;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.application.token.RefreshTokenIssuer;
import com.pheeeew.device.domain.DeviceChallenge;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceChallengeRepository;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppAttestRegistrationIntegrationTest {

    private static final String 발급되지_않은_challenge = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String 만료된_challenge = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String 승인_지표 = "pheeeew.device.appattest.accepted";
    private static final String 거절_지표 = "pheeeew.device.appattest.rejected";
    private static final int 토큰_상한_길이 = 32_768;

    @Autowired
    private DeviceChallengeService deviceChallengeService;

    @Autowired
    private DeviceChallengeRepository deviceChallengeRepository;

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

    private SimpleMeterRegistry registry;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        deviceService = 등록_서비스를_만든다(설정된_app_attest_설정(), 합성_루트를_신뢰하는_체인_검증기());
        challenge_를_저장한다(증명에_묶인_challenge);
        challenge_를_저장한다(증명에_묶이지_않은_challenge);
    }

    @AfterEach
    void tearDown() {
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        deviceChallengeRepository.deleteAllInBatch();
    }

    @Test
    void 정상_증명이면_기기를_등록하고_challenge_를_소모한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        DeviceSaveResult result = deviceService.save(UUID.randomUUID(), 증명);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(deviceRepository.count()).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNotNull();
        assertThat(승인_수()).isOne();
    }

    @Test
    void 같은_challenge_로는_두_번_등록할_수_없다() {
        // given
        deviceService.save(UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자)
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(deviceRepository.count()).isOne();
        assertThat(승인_수()).isOne();
    }

    @Test
    void 애플_루트로_이어지지_않는_체인은_거절한다() {
        // given
        DeviceService 애플_루트를_쓰는_서비스 =
                등록_서비스를_만든다(설정된_app_attest_설정(), new AppAttestCertificateChainValidator());

        // when
        Throwable throwable = catchThrowable(() -> 애플_루트를_쓰는_서비스.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자)
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("CERTIFICATE_CHAIN_INVALID")).isOne();
        assertThat(deviceRepository.count()).isZero();
        assertThat(승인_수()).isZero();
    }

    @Test
    void 검증에_실패해도_challenge_는_소모되어_다시_쓸_수_없다() {
        // given
        DeviceService 애플_루트를_쓰는_서비스 =
                등록_서비스를_만든다(설정된_app_attest_설정(), new AppAttestCertificateChainValidator());
        Throwable 체인_검증_실패 = catchThrowable(() -> 애플_루트를_쓰는_서비스.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자)
        ));

        // when
        Throwable 같은_challenge_로_다시 = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자)
        ));

        // then
        증명을_확인할_수_없다(체인_검증_실패);
        challenge_를_쓸_수_없다(같은_challenge_로_다시);
        assertThat(소모_시각(증명에_묶인_challenge)).isNotNull();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 증명에_묶이지_않은_challenge_를_보내면_nonce_가_어긋나_거절하고_그_challenge_도_소모한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(정품_증명(), 증명에_묶이지_않은_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("NONCE_MISMATCH")).isOne();
        assertThat(소모_시각(증명에_묶이지_않은_challenge)).isNotNull();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @Test
    void 다른_App_ID_로_만든_증명은_거절한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(다른_앱으로_만든_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("APP_ID_MISMATCH")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void counter_가_0이_아니면_거절한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(카운터가_1인_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("COUNTER_NOT_ZERO")).isOne();
    }

    @ParameterizedTest
    @MethodSource("개발_빌드_증명들")
    void 운영_환경_설정은_개발_빌드_증명을_거절한다(String 개발_빌드_증명) {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(개발_빌드_증명, 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("ENVIRONMENT_MISMATCH")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("개발_빌드_증명들")
    void 개발_환경_설정은_애플_문서가_갈리는_두_aaguid_를_모두_받는다(String 개발_빌드_증명) {
        // given
        DeviceService 개발_환경_서비스 = 등록_서비스를_만든다(개발_환경_설정(), 합성_루트를_신뢰하는_체인_검증기());

        // when
        DeviceSaveResult result = 개발_환경_서비스.save(
                UUID.randomUUID(), 증명을_담은_요청(개발_빌드_증명, 증명에_묶인_challenge, 키_식별자)
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(승인_수()).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNotNull();
    }

    @Test
    void authData_의_키_식별자가_요청의_keyId_와_다르면_거절한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(키_식별자가_어긋난_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("CREDENTIAL_ID_MISMATCH")).isOne();
    }

    @Test
    void keyId_는_비압축_점의_해시여야_하고_DER_공개키_정보의_해시로는_통과하지_않는다() {
        // given
        DeviceAttestation 증명 =
                증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, DER_공개키_정보를_해시한_키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("KEY_ID_MISMATCH")).isOne();
        assertThat(승인_수()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   ", "키 식별자가 아닙니다", "AAAAAAAAAAAAAAAAAAAAAAA="})
    void keyId_가_32바이트_base64_가_아니면_challenge_를_소모하지_않고_거절한다(String keyId) {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, keyId);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("KEY_ID_MISSING")).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @Test
    void base64_가_아니거나_상한을_넘는_토큰은_challenge_를_소모하지_않고_거절한다() {
        // given
        DeviceAttestation base64_가_아닌_증명 =
                증명을_담은_요청("증명이 아닙니다", 증명에_묶인_challenge, 키_식별자);
        DeviceAttestation 상한을_넘는_증명 =
                증명을_담은_요청("A".repeat(토큰_상한_길이 + 1), 증명에_묶이지_않은_challenge, 키_식별자);

        // when
        Throwable base64_실패 = catchThrowable(() -> deviceService.save(UUID.randomUUID(), base64_가_아닌_증명));
        Throwable 상한_초과_실패 = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 상한을_넘는_증명));

        // then
        증명을_확인할_수_없다(base64_실패);
        증명을_확인할_수_없다(상한_초과_실패);
        assertThat(거절_수("MALFORMED_TOKEN")).isEqualTo(2);
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
        assertThat(소모_시각(증명에_묶이지_않은_challenge)).isNull();
    }

    @Test
    void 형식이_다른_증명은_challenge_를_소모하지_않고_거절한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(형식이_다른_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("UNSUPPORTED_FORMAT")).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @Test
    void authData_가_짧은_증명은_challenge_를_소모하지_않고_거절한다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(authData가_86바이트인_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("MALFORMED_AUTH_DATA")).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   "})
    void challenge_가_없으면_400_이고_아무것도_소모하지_않는다(String challenge) {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(정품_증명(), challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(거절_수("CHALLENGE_INVALID")).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 발급되지_않은_challenge_와_만료된_challenge_는_400_이다() {
        // given
        만료된_challenge_를_저장한다();

        // when
        Throwable 발급되지_않은_경우 = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 발급되지_않은_challenge, 키_식별자)
        ));
        Throwable 만료된_경우 = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 만료된_challenge, 키_식별자)
        ));

        // then
        challenge_를_쓸_수_없다(발급되지_않은_경우);
        challenge_를_쓸_수_없다(만료된_경우);
        assertThat(거절_수("CHALLENGE_INVALID")).isEqualTo(2);
        assertThat(소모_시각(만료된_challenge)).isNull();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 설정이_있어도_증명을_보내지_않으면_검증하지_않고_통과시킨다() {
        // given
        DeviceAttestation 증명을_보내지_않는_요청 = DeviceAttestation.of(DevicePlatform.IOS, null, null, null);

        // when
        DeviceSaveResult result = deviceService.save(UUID.randomUUID(), 증명을_보내지_않는_요청);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(deviceRepository.count()).isOne();
        assertThat(승인_수()).isZero();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @Test
    void 설정이_없으면_증명을_보낸_등록만_거절하고_증명을_보내지_않은_등록은_통과시킨다() {
        // given
        DeviceService 설정이_없는_서비스 =
                등록_서비스를_만든다(설정이_없는_app_attest_설정(), 합성_루트를_신뢰하는_체인_검증기());

        // when
        Throwable 증명을_보낸_등록 = catchThrowable(() -> 설정이_없는_서비스.save(
                UUID.randomUUID(), 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자)
        ));
        DeviceSaveResult 증명_없는_등록 = 설정이_없는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.IOS, null, null, null)
        );

        // then
        증명을_확인할_수_없다(증명을_보낸_등록);
        assertThat(거절_수("PROPERTIES_MISSING")).isOne();
        assertThat(증명_없는_등록.created()).isTrue();
        assertThat(deviceRepository.count()).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isNull();
    }

    @Test
    void 강제를_켜면_증명을_보내지_않은_등록도_거절한다() {
        // given
        DeviceService 강제하는_서비스 = 등록_서비스를_만든다(강제가_켜진_설정(), 합성_루트를_신뢰하는_체인_검증기());

        // when
        Throwable throwable = catchThrowable(() -> 강제하는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.IOS, null, null, null)
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(거절_수("ATTESTATION_REQUIRED")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 재시도_경로는_증명을_다시_검증하지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceAttestation 증명 = 증명을_담은_요청(정품_증명(), 증명에_묶인_challenge, 키_식별자);
        deviceService.save(requestId, 증명);
        Instant 최초_소모_시각 = 소모_시각(증명에_묶인_challenge);

        // when
        DeviceSaveResult 재시도 = deviceService.save(requestId, 증명);

        // then
        assertThat(재시도.created()).isFalse();
        assertThat(재시도.refreshToken()).isNotBlank();
        assertThat(승인_수()).isOne();
        assertThat(소모_시각(증명에_묶인_challenge)).isEqualTo(최초_소모_시각);
        assertThat(deviceRepository.count()).isOne();
    }

    @Test
    void 검증_경로는_challenge_와_증명_토큰을_예외에_남기지_않는다() {
        // given
        DeviceAttestation 증명 = 증명을_담은_요청(다른_앱으로_만든_증명(), 증명에_묶인_challenge, 키_식별자);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), 증명));

        // then
        assertThat(throwable).hasMessage("무결성 증명을 확인할 수 없습니다.");
        assertThat(throwable.getCause()).isNull();
        assertThat(throwable.getMessage())
                .doesNotContain(증명에_묶인_challenge)
                .doesNotContain(키_식별자);
    }

    static String[] 개발_빌드_증명들() {
        return new String[]{개발_증명(), 샌드박스_증명()};
    }

    private DeviceService 등록_서비스를_만든다(
            AppAttestProperties properties,
            AppAttestCertificateChainValidator chainValidator
    ) {
        AppAttestDeviceAttestationVerifier verifier = new AppAttestDeviceAttestationVerifier(
                new AppAttestObjectDecoder(),
                chainValidator,
                deviceChallengeService,
                properties,
                new AppAttestMetrics(registry)
        );

        return new DeviceService(
                deviceRepository,
                refreshTokenIssuer,
                accessTokenIssuer,
                verifier,
                tokenProperties
        );
    }

    private DeviceAttestation 증명을_담은_요청(String token, String challenge, String keyId) {
        return DeviceAttestation.of(DevicePlatform.IOS, token, challenge, keyId);
    }

    private void challenge_를_저장한다(String challenge) {
        deviceChallengeRepository.save(DeviceChallenge.builder()
                .challenge(challenge)
                .expiresAt(Instant.now().plus(tokenProperties.challengeTtl()))
                .build());
    }

    private void 만료된_challenge_를_저장한다() {
        deviceChallengeRepository.save(DeviceChallenge.builder()
                .challenge(만료된_challenge)
                .expiresAt(Instant.now().minusSeconds(1))
                .build());
    }

    private Instant 소모_시각(String challenge) {
        return deviceChallengeRepository.findAll().stream()
                .filter(deviceChallenge -> deviceChallenge.getChallenge().equals(challenge))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("challenge 행이 없습니다."))
                .getConsumedAt();
    }

    private double 승인_수() {
        return registry.get(승인_지표).counter().count();
    }

    private double 거절_수(String reason) {
        Counter counter = registry.find(거절_지표).tag("reason", reason).counter();

        return counter == null ? 0D : counter.count();
    }

    private void 증명을_확인할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_INVALID);
    }

    private void challenge_를_쓸_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_CHALLENGE_INVALID);
    }
}
