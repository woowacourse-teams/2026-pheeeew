package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.JWE_무결성_토큰;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.JWS_무결성_토큰;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.강제가_켜진_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.검증을_건너뛰는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.복호화_응답;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.우리_패키지명;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.자격증명이_없는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.자격증명이_있는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.정품_복호화_응답;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.DeviceChallengeService;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.dto.DeviceAttestation;
import com.pheeeew.device.application.dto.DeviceChallengeResult;
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
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlayIntegrityRegistrationIntegrationTest {

    private static final String 발급되지_않은_challenge = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String 만료된_challenge = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String 토큰이_담은_다른_nonce = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
    private static final int 동시_요청_수 = 6;

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

    private FakeGoogleApiServer 가짜_구글;
    private SimpleMeterRegistry registry;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        가짜_구글 = FakeGoogleApiServer.시작한다();
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.access", 3599));
        registry = new SimpleMeterRegistry();
        deviceService = 등록_서비스를_만든다(자격증명이_있는_설정(가짜_구글.토큰_엔드포인트()));
    }

    @AfterEach
    void tearDown() {
        가짜_구글.close();
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        deviceChallengeRepository.deleteAllInBatch();
    }

    @Test
    void 정품_판정이면_기기를_등록하고_challenge_를_소모한다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));

        // when
        DeviceSaveResult result = deviceService.save(UUID.randomUUID(), 증명을_담은_요청(발급.challenge()));

        // then
        assertThat(result.created()).isTrue();
        assertThat(deviceRepository.count()).isOne();
        assertThat(소모_시각(발급.challenge())).isNotNull();
        assertThat(카운터("pheeeew.device.attestation.accepted")).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "integrity-token-from-app",
            "eyJhbGciOiJBMjU2S1cifQ.onlyTwoSegments",
            ".leadingDotIsNotASegment.a.b.c",
            "eyJhbGciOiJBMjU2S1cifQ.has space.a.b.c",
            "eyJhbGciOiJBMjU2S1cifQ.has+plus/and=padding.a.b.c",
            "eyJhbGciOiJBMjU2S1cifQ.a.b.c.d\n"
    })
    void 컴팩트_직렬화_형태가_아닌_토큰은_구글을_한_번도_부르지_않고_거절한다(String 형태가_아닌_토큰) {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, 형태가_아닌_토큰, 발급.challenge(), null)
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(구글_호출_수()).isZero();
        assertThat(거절_카운터("MALFORMED_TOKEN")).isOne();
        assertThat(deviceRepository.count()).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {JWS_무결성_토큰, JWE_무결성_토큰})
    void 세_조각_JWS_와_다섯_조각_JWE_는_모두_형태_검사를_통과하고_구글에_잘리지_않은_채_전달된다(String 무결성_토큰) {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));

        // when
        DeviceSaveResult result = deviceService.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, 무결성_토큰, 발급.challenge(), null)
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(가짜_구글.복호화_요청_수()).isOne();
        assertThat(가짜_구글.마지막_복호화_요청().body())
                .contains("\"integrity_token\":\"" + 무결성_토큰 + "\"");
        assertThat(거절_카운터("MALFORMED_TOKEN")).isZero();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   "})
    void 증명_토큰을_보냈는데_challenge_가_없으면_구글을_부르지_않고_거절한다(String challenge) {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(challenge)
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(구글_호출_수()).isZero();
        assertThat(거절_카운터("CHALLENGE_MISSING")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 발급하지_않은_challenge_를_보내면_복호화하지_않고_거절한다() {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급되지_않은_challenge)
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(구글_호출_수()).isZero();
        assertThat(거절_카운터("CHALLENGE_UNUSABLE")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 만료된_challenge_를_보내면_복호화하지_않고_거절하며_행은_남는다() {
        // given
        만료된_challenge_를_저장한다();

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(만료된_challenge)
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(구글_호출_수()).isZero();
        assertThat(거절_카운터("CHALLENGE_UNUSABLE")).isOne();
        assertThat(소모_시각(만료된_challenge)).isNull();
    }

    @Test
    void 이미_소모된_challenge_를_보내면_복호화하지_않고_거절한다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));
        deviceService.save(UUID.randomUUID(), 증명을_담은_요청(발급.challenge()));
        long 최초_등록까지의_구글_호출_수 = 구글_호출_수();

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(구글_호출_수()).isEqualTo(최초_등록까지의_구글_호출_수);
        assertThat(거절_카운터("CHALLENGE_UNUSABLE")).isOne();
        assertThat(deviceRepository.count()).isOne();
    }

    @Test
    void challenge_사전_조회는_읽기_전용이라_거절_뒤에도_같은_challenge_로_등록할_수_있다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        Throwable 형태가_틀린_요청 = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, "junk", 발급.challenge(), null)
        ));

        // when
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));
        DeviceSaveResult 다시_등록 = deviceService.save(UUID.randomUUID(), 증명을_담은_요청(발급.challenge()));

        // then
        증명을_확인할_수_없다(형태가_틀린_요청);
        assertThat(다시_등록.created()).isTrue();
        assertThat(소모_시각(발급.challenge())).isNotNull();
    }

    @Test
    void 토큰이_담은_nonce_가_요청한_challenge_와_다르면_거절하고_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(토큰이_담은_다른_nonce));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(거절_카운터("CHALLENGE_MISMATCH")).isOne();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 증명_토큰을_보내지_않으면_challenge_를_아예_보지_않고_등록한다() {
        // given
        DeviceAttestation 토큰_없이_쓸_수_없는_challenge_만_담은_요청 =
                DeviceAttestation.of(DevicePlatform.ANDROID, null, 발급되지_않은_challenge, null);

        // when
        DeviceSaveResult 토큰도_challenge_도_없는_등록 =
                deviceService.save(UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, null, null, null));
        DeviceSaveResult challenge_만_있는_등록 =
                deviceService.save(UUID.randomUUID(), 토큰_없이_쓸_수_없는_challenge_만_담은_요청);

        // then
        assertThat(토큰도_challenge_도_없는_등록.created()).isTrue();
        assertThat(challenge_만_있는_등록.created()).isTrue();
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(구글_호출_수()).isZero();
    }

    @Test
    void 같은_challenge_를_담은_증명으로는_두_번_등록할_수_없다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));
        deviceService.save(UUID.randomUUID(), 증명을_담은_요청(발급.challenge()));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        challenge_를_쓸_수_없다(throwable);
        assertThat(deviceRepository.count()).isOne();
    }

    @Test
    void 앱_판정이_PLAY_RECOGNIZED_가_아니면_거절하고_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 복호화_응답(
                우리_패키지명, 발급.challenge(), "UNRECOGNIZED_VERSION", "\"MEETS_DEVICE_INTEGRITY\""
        ));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(deviceRepository.count()).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(거절_카운터("APP_UNRECOGNIZED")).isOne();
        assertThat(판정_카운터("UNRECOGNIZED_VERSION", true)).isOne();
    }

    @Test
    void 기기_판정에_MEETS_DEVICE_INTEGRITY_가_없으면_거절하고_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 복호화_응답(
                우리_패키지명, 발급.challenge(), "PLAY_RECOGNIZED", "\"MEETS_BASIC_INTEGRITY\""
        ));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(deviceRepository.count()).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(거절_카운터("DEVICE_INTEGRITY_MISSING")).isOne();
        assertThat(판정_카운터("PLAY_RECOGNIZED", false)).isOne();
    }

    @Test
    void 패키지명이_다르면_거절하고_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 복호화_응답(
                "com.attacker.app", 발급.challenge(), "PLAY_RECOGNIZED", "\"MEETS_DEVICE_INTEGRITY\""
        ));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(deviceRepository.count()).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(거절_카운터("PACKAGE_MISMATCH")).isOne();
    }

    @Test
    void 구글_호출이_실패하면_거절하지_않고_우리_쪽_장애로_올리며_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(503, "{\"error\":{\"code\":503}}");

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
        assertThat(deviceRepository.count()).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(거절_카운터("GOOGLE_UNAVAILABLE")).isOne();
        assertThat(거절_카운터("GOOGLE_QUOTA_EXHAUSTED")).isZero();
    }

    @Test
    void 구글_할당량이_소진되면_상류_장애와_다른_사유로_기록하고_1분_뒤_재시도를_알리며_challenge_를_남긴다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE);
        assertThat(((DeviceException) throwable).getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(거절_카운터("GOOGLE_QUOTA_EXHAUSTED")).isOne();
        assertThat(거절_카운터("GOOGLE_UNAVAILABLE")).isZero();
        assertThat(소모_시각(발급.challenge())).isNull();
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 자격증명이_없으면_증명을_보낸_등록만_거절한다() {
        // given
        DeviceService 자격증명_없는_서비스 = 등록_서비스를_만든다(자격증명이_없는_설정());

        // when
        Throwable 증명을_보낸_등록 = catchThrowable(
                () -> 자격증명_없는_서비스.save(UUID.randomUUID(), 증명을_담은_요청(발급되지_않은_challenge))
        );
        DeviceSaveResult 증명_없는_등록 = 자격증명_없는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, null, null, null)
        );

        // then
        증명을_확인할_수_없다(증명을_보낸_등록);
        assertThat(증명_없는_등록.created()).isTrue();
        assertThat(deviceRepository.count()).isOne();
        assertThat(거절_카운터("CREDENTIALS_MISSING")).isOne();
        assertThat(구글_호출_수()).isZero();
    }

    @Test
    void 증명을_보낸_IOS_등록은_거절한다() {
        // given
        DeviceAttestation ios_증명 =
                DeviceAttestation.of(DevicePlatform.IOS, JWE_무결성_토큰, 발급되지_않은_challenge, null);

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(UUID.randomUUID(), ios_증명));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(deviceRepository.count()).isZero();
        assertThat(거절_카운터("UNSUPPORTED_PLATFORM")).isOne();
        assertThat(구글_호출_수()).isZero();
    }

    @Test
    void 강제를_켜면_증명을_보내지_않은_등록도_거절한다() {
        // given
        DeviceService 강제하는_서비스 = 등록_서비스를_만든다(강제가_켜진_설정(가짜_구글.토큰_엔드포인트()));

        // when
        Throwable throwable = catchThrowable(() -> 강제하는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, null, null, null)
        ));

        // then
        증명을_확인할_수_없다(throwable);
        assertThat(deviceRepository.count()).isZero();
        assertThat(거절_카운터("ATTESTATION_REQUIRED")).isOne();
    }

    @Test
    void 검증을_건너뛰면_형태가_아닌_토큰도_challenge_없이_통과하고_구글을_부르지_않는다() {
        // given
        DeviceService 건너뛰는_서비스 = 등록_서비스를_만든다(검증을_건너뛰는_설정(가짜_구글.토큰_엔드포인트()));

        // when
        DeviceSaveResult 안드로이드_등록 = 건너뛰는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.ANDROID, "junk", null, null)
        );
        DeviceSaveResult 아이오에스_등록 = 건너뛰는_서비스.save(
                UUID.randomUUID(), DeviceAttestation.of(DevicePlatform.IOS, "junk", null, null)
        );

        // then
        assertThat(안드로이드_등록.created()).isTrue();
        assertThat(아이오에스_등록.created()).isTrue();
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(구글_호출_수()).isZero();
        assertThat(카운터("pheeeew.device.attestation.skipped")).isEqualTo(2);
        assertThat(카운터("pheeeew.device.attestation.accepted")).isZero();
    }

    @Test
    void 재시도_경로는_증명을_다시_검증하지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));
        deviceService.save(requestId, 증명을_담은_요청(발급.challenge()));
        long 최초_등록의_복호화_호출_수 = 가짜_구글.복호화_요청_수();

        // when
        DeviceSaveResult 재시도 = deviceService.save(requestId, 증명을_담은_요청(발급.challenge()));

        // then
        assertThat(재시도.created()).isFalse();
        assertThat(재시도.refreshToken()).isNotBlank();
        assertThat(가짜_구글.복호화_요청_수()).isEqualTo(최초_등록의_복호화_호출_수);
        assertThat(deviceRepository.count()).isOne();
    }

    @Test
    void 같은_challenge_를_담은_동시_등록은_한_건만_성공하고_경합에_진_요청은_최초_결과_대신_400_을_받는다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(발급.challenge()));

        // when
        List<Object> 결과들 = 같은_요청으로_동시에_등록한다(requestId, 증명을_담은_요청(발급.challenge()));

        // then
        assertThat(결과들.stream().filter(DeviceSaveResult.class::isInstance)).hasSize(1);
        assertThat(결과들.stream().filter(DeviceException.class::isInstance))
                .hasSize(동시_요청_수 - 1)
                .allSatisfy(실패 -> assertThat(((DeviceException) 실패).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_CHALLENGE_INVALID));
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
        assertThat(소모_시각(발급.challenge())).isNotNull();
    }

    @Test
    void 검증_경로는_challenge_값과_무결성_토큰을_예외에_남기지_않는다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        가짜_구글.복호화_응답을_넣는다(200, 복호화_응답(
                우리_패키지명, 발급.challenge(), "UNEVALUATED", "\"MEETS_BASIC_INTEGRITY\""
        ));

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), 증명을_담은_요청(발급.challenge())
        ));

        // then
        assertThat(throwable).hasMessage("무결성 증명을 확인할 수 없습니다.");
        assertThat(throwable.getMessage())
                .doesNotContain(발급.challenge())
                .doesNotContain(JWE_무결성_토큰);
    }

    private DeviceService 등록_서비스를_만든다(PlayIntegrityProperties properties) {
        PlayIntegrityMetrics metrics = new PlayIntegrityMetrics(registry);
        PlayIntegrityTokenDecoder decoder = new PlayIntegrityTokenDecoder(
                가짜_구글.이_서버를_향하는_클라이언트(),
                new GoogleAccessTokenProvider(RestClient.builder().build(), properties),
                properties
        );
        PlayIntegrityDeviceAttestationVerifier verifier = new PlayIntegrityDeviceAttestationVerifier(
                decoder,
                deviceChallengeService,
                properties,
                metrics
        );

        return new DeviceService(
                deviceRepository,
                refreshTokenIssuer,
                accessTokenIssuer,
                verifier,
                tokenProperties
        );
    }

    private DeviceAttestation 증명을_담은_요청(String challenge) {
        return DeviceAttestation.of(DevicePlatform.ANDROID, JWE_무결성_토큰, challenge, null);
    }

    private void 만료된_challenge_를_저장한다() {
        deviceChallengeRepository.save(DeviceChallenge.builder()
                .challenge(만료된_challenge)
                .expiresAt(Instant.now().minusSeconds(1))
                .build());
    }

    private List<Object> 같은_요청으로_동시에_등록한다(UUID requestId, DeviceAttestation attestation) throws Exception {
        CountDownLatch ready = new CountDownLatch(동시_요청_수);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> 결과들 = new ArrayList<>();

        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_요청_수)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                Callable<Object> 등록 = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        return deviceService.save(requestId, attestation);
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                };
                futures.add(executorService.submit(등록));
            }

            boolean 모두_준비됨 = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(모두_준비됨).isTrue();
            for (Future<Object> future : futures) {
                결과들.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        return 결과들;
    }

    private long 구글_호출_수() {
        return 가짜_구글.토큰_요청_수() + 가짜_구글.복호화_요청_수();
    }

    private Instant 소모_시각(String challenge) {
        DeviceChallenge 저장된_challenge = deviceChallengeRepository.findAll().stream()
                .filter(deviceChallenge -> deviceChallenge.getChallenge().equals(challenge))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("challenge 행이 없습니다."));

        return 저장된_challenge.getConsumedAt();
    }

    private double 카운터(String name) {
        return registry.get(name).counter().count();
    }

    private double 거절_카운터(String reason) {
        Counter counter = registry.find("pheeeew.device.attestation.rejected")
                .tag("reason", reason)
                .counter();

        return counter == null ? 0D : counter.count();
    }

    private double 판정_카운터(String appRecognitionVerdict, boolean meetsDeviceIntegrity) {
        return registry.get("pheeeew.device.attestation.verdict")
                .tag("appRecognition", appRecognitionVerdict)
                .tag("deviceIntegrity", Boolean.toString(meetsDeviceIntegrity))
                .counter()
                .count();
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
