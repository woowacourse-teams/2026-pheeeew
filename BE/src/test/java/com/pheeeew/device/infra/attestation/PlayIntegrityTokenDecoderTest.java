package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.복호화_응답;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.우리_패키지명;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.자격증명이_있는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.정품_복호화_응답;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class PlayIntegrityTokenDecoderTest {

    private static final String CHALLENGE = "PjONwTh56rDaOsphVQQeqQcPyQtLZL-reX5Us2xD2SM";
    private static final String 무결성_토큰 = "integrity-token-from-app";

    private FakeGoogleApiServer 가짜_구글;
    private PlayIntegrityTokenDecoder decoder;

    @BeforeEach
    void setUp() {
        가짜_구글 = FakeGoogleApiServer.시작한다();
        PlayIntegrityProperties properties = 자격증명이_있는_설정(가짜_구글.토큰_엔드포인트());
        RestClient client = 가짜_구글.이_서버를_향하는_클라이언트();
        decoder = new PlayIntegrityTokenDecoder(
                client,
                new GoogleAccessTokenProvider(RestClient.builder().build(), properties),
                properties
        );
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.access", 3599));
    }

    @AfterEach
    void tearDown() {
        가짜_구글.close();
    }

    @Test
    void 복호화_응답에서_패키지명과_challenge_와_판정을_뽑는다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(CHALLENGE));

        // when
        PlayIntegrityPayload payload = decoder.decode(무결성_토큰);

        // then
        assertThat(payload.requestPackageName()).isEqualTo(우리_패키지명);
        assertThat(payload.challenge()).isEqualTo(CHALLENGE);
        assertThat(payload.appRecognitionVerdict()).isEqualTo("PLAY_RECOGNIZED");
        assertThat(payload.deviceRecognitionVerdicts())
                .containsExactly("MEETS_DEVICE_INTEGRITY", "MEETS_BASIC_INTEGRITY");
    }

    @Test
    void 복호화_요청은_패키지명이_박힌_경로로_콜론을_인코딩하지_않고_보낸다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(CHALLENGE));

        // when
        decoder.decode(무결성_토큰);

        // then
        assertThat(가짜_구글.마지막_복호화_요청().path())
                .isEqualTo("/v1/" + 우리_패키지명 + ":decodeIntegrityToken");
        assertThat(가짜_구글.마지막_복호화_요청().method()).isEqualTo("POST");
    }

    @Test
    void 복호화_요청은_액세스_토큰을_Bearer_로_싣고_무결성_토큰을_본문에_담는다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(CHALLENGE));

        // when
        decoder.decode(무결성_토큰);

        // then
        assertThat(가짜_구글.마지막_복호화_요청().authorization()).isEqualTo("Bearer ya29.access");
        assertThat(가짜_구글.마지막_복호화_요청().body()).contains("\"integrity_token\":\"" + 무결성_토큰 + "\"");
    }

    @Test
    void 구글이_400_을_주면_증명_확인_실패로_바꾼다() {
        // given
        가짜_구글.복호화_응답을_넣는다(400, "{\"error\":{\"code\":400,\"message\":\"Integrity token is invalid\"}}");

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        증명을_확인할_수_없다(throwable);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500, 503})
    void 구글이_400_도_429_도_아닌_오류를_주면_우리_쪽_장애로_올린다(int status) {
        // given
        가짜_구글.복호화_응답을_넣는다(status, "{\"error\":{\"code\":%d}}".formatted(status));

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
    }

    @Test
    void 구글이_429_를_주면_증명_확인_불가와_1분_뒤_재시도로_바꾼다() {
        // given
        가짜_구글.복호화_응답을_넣는다(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE);
        assertThat(((DeviceException) throwable).getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"tokenPayloadExternal\": {}}",
            "{\"tokenPayloadExternal\": {\"requestDetails\": {\"nonce\": \"x\"}}}",
            "{\"tokenPayloadExternal\": {\"requestDetails\": {\"requestPackageName\": \"com.pheeeew\"},"
                    + " \"appIntegrity\": {}, \"deviceIntegrity\": {}}}",
            "{\"tokenPayloadExternal\": \"not-an-object\"}"
    })
    void 응답_모양이_가정과_다르면_증명_확인_실패로_바꾼다(String body) {
        // given
        가짜_구글.복호화_응답을_넣는다(200, body);

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        증명을_확인할_수_없다(throwable);
    }

    @Test
    void 기기_판정이_배열이_아니면_증명_확인_실패로_바꾼다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, """
                {
                  "tokenPayloadExternal": {
                    "requestDetails": {"requestPackageName": "com.pheeeew", "nonce": "%s"},
                    "appIntegrity": {"appRecognitionVerdict": "PLAY_RECOGNIZED"},
                    "deviceIntegrity": {"deviceRecognitionVerdict": "MEETS_DEVICE_INTEGRITY"}
                  }
                }
                """.formatted(CHALLENGE));

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        증명을_확인할_수_없다(throwable);
    }

    @Test
    void 기기_판정이_빈_배열이면_판정_없음으로_읽는다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, 복호화_응답(우리_패키지명, CHALLENGE, "PLAY_RECOGNIZED", ""));

        // when
        PlayIntegrityPayload payload = decoder.decode(무결성_토큰);

        // then
        assertThat(payload.deviceRecognitionVerdicts()).isEmpty();
    }

    @Test
    void 복호화_실패_예외에_제출한_무결성_토큰과_challenge_가_들어가지_않는다() {
        // given
        가짜_구글.복호화_응답을_넣는다(400, "{\"error\":{\"message\":\"invalid\"}}");

        // when
        Throwable throwable = catchThrowable(() -> decoder.decode(무결성_토큰));

        // then
        assertThat(throwable).hasMessage("무결성 증명을 확인할 수 없습니다.");
        assertThat(throwable.getMessage()).doesNotContain(무결성_토큰);
        assertThat(throwable.getMessage()).doesNotContain(CHALLENGE);
    }

    @Test
    void 복호화_결과_객체는_toString_에_어떤_값도_드러내지_않는다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, 정품_복호화_응답(CHALLENGE));

        // when
        PlayIntegrityPayload payload = decoder.decode(무결성_토큰);

        // then
        assertThat(payload.toString())
                .doesNotContain(CHALLENGE)
                .doesNotContain(우리_패키지명)
                .doesNotContain("PLAY_RECOGNIZED");
    }

    private void 증명을_확인할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_INVALID);
    }
}
