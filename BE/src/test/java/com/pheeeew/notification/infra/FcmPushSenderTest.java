package com.pheeeew.notification.infra;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nimbusds.jwt.SignedJWT;
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import com.pheeeew.device.infra.attestation.PlayIntegrityUnavailableException;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.json.JsonParserFactory;

class FcmPushSenderTest {

    private static final String 프로젝트_ID = "pheeeew-test";
    private static final PushMessage 메시지 = PushMessage.of("제목", "본문", Map.of("type", "COLLECTION_CLAIMABLE"));

    private FakeGoogleApiServer 가짜_구글;
    private FcmPushSender sender;

    @BeforeEach
    void setUp() {
        가짜_구글 = FakeGoogleApiServer.시작한다();
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.fcm", 3599));
        sender = new FcmPushSender(가짜_구글.이_서버를_향하는_클라이언트(),
                new FcmProperties(프로젝트_ID, 서비스_계정_설정(가짜_구글.토큰_엔드포인트())));
    }

    @AfterEach
    void tearDown() {
        가짜_구글.close();
    }

    @Test
    void 프로젝트_경로로_액세스_토큰과_등록_값_알림_데이터를_보낸다() {
        // given
        가짜_구글.복호화_응답을_넣는다(200, "{\"name\":\"projects/pheeeew-test/messages/1\"}");

        // when
        PushSendResult result = sender.send(기본_등록_값, 메시지);

        // then
        assertThat(result).isEqualTo(PushSendResult.SENT);
        var request = 가짜_구글.마지막_복호화_요청();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/projects/" + 프로젝트_ID + "/messages:send");
        assertThat(request.authorization()).isEqualTo("Bearer ya29.fcm");
        assertThat(JsonParserFactory.getJsonParser().parseMap(request.body())).isEqualTo(Map.of("message", Map.of(
                "token", 기본_등록_값,
                "notification", Map.of("title", "제목", "body", "본문"),
                "data", Map.of("type", "COLLECTION_CLAIMABLE")
        )));
    }

    @Test
    void 액세스_토큰은_FCM_발송_범위로_받는다() throws Exception {
        // given
        가짜_구글.복호화_응답을_넣는다(200, "{}");

        // when
        sender.send(기본_등록_값, 메시지);

        // then
        String assertion = Arrays.stream(가짜_구글.받은_요청들().getFirst().body().split("&"))
                .filter(pair -> pair.startsWith("assertion="))
                .map(pair -> URLDecoder.decode(pair.substring("assertion=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
        assertThat(SignedJWT.parse(assertion).getJWTClaimsSet().getStringClaim("scope"))
                .isEqualTo("https://www.googleapis.com/auth/firebase.messaging");
    }

    @Test
    void FCM이_UNREGISTERED로_응답하면_무효_등록_값으로_알린다() {
        // given
        가짜_구글.복호화_응답을_넣는다(404, 오류_응답(404, "NOT_FOUND", "UNREGISTERED"));

        // when
        PushSendResult result = sender.send(기본_등록_값, 메시지);

        // then
        assertThat(result).isEqualTo(PushSendResult.UNREGISTERED);
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_ARGUMENT, REJECTED",
            "401, UNAUTHENTICATED, REJECTED",
            "403, SENDER_ID_MISMATCH, REJECTED",
            "404, NOT_FOUND, REJECTED",
            "429, QUOTA_EXCEEDED, RETRYABLE",
            "500, INTERNAL, RETRYABLE",
            "503, UNAVAILABLE, RETRYABLE"
    })
    void 무효_등록_값이_아닌_오류는_일시_오류와_영구_거절로_나눈다(int status, String errorCode, PushSendResult expected) {
        // given
        가짜_구글.복호화_응답을_넣는다(status, 오류_응답(status, errorCode, errorCode));

        // when
        PushSendResult result = sender.send(기본_등록_값, 메시지);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void 응답이_읽기_제한_시간_안에_오지_않으면_일시_오류로_알린다() {
        // given
        FcmPushSender 짧게_기다리는_발송기 = new FcmPushSender(가짜_구글.이_서버를_향하는_클라이언트(Duration.ofMillis(300)),
                new FcmProperties(프로젝트_ID, 서비스_계정_설정(가짜_구글.토큰_엔드포인트())));
        가짜_구글.복호화_응답을_보내지_않는다();

        // when
        PushSendResult result = 짧게_기다리는_발송기.send(기본_등록_값, 메시지);

        // then
        assertThat(result).isEqualTo(PushSendResult.RETRYABLE);
        assertThat(가짜_구글.복호화_요청_수()).isOne();
    }

    @Test
    void 액세스_토큰을_받지_못하면_발송하지_않고_예외를_던진다() {
        // given
        FakeGoogleApiServer 토큰을_주지_않는_구글 = FakeGoogleApiServer.시작한다();
        토큰을_주지_않는_구글.토큰_응답을_넣는다(503, "{\"error\":\"unavailable\"}");
        FcmPushSender 토큰_없는_발송기 = new FcmPushSender(토큰을_주지_않는_구글.이_서버를_향하는_클라이언트(),
                new FcmProperties(프로젝트_ID, 서비스_계정_설정(토큰을_주지_않는_구글.토큰_엔드포인트())));

        // when
        Throwable thrown = catchThrowable(() -> 토큰_없는_발송기.send(기본_등록_값, 메시지));

        // then
        토큰을_주지_않는_구글.close();
        assertThat(thrown).isInstanceOf(PlayIntegrityUnavailableException.class);
        assertThat(토큰을_주지_않는_구글.복호화_요청_수()).isZero();
    }

    @Test
    void 서비스_계정_키가_없으면_발송기를_쓸_수_없다() {
        // given
        FcmPushSender 키_없는_발송기 = new FcmPushSender(가짜_구글.이_서버를_향하는_클라이언트(),
                new FcmProperties(프로젝트_ID, ""));

        // when
        boolean available = 키_없는_발송기.isAvailable();

        // then
        assertThat(available).isFalse();
        assertThat(sender.isAvailable()).isTrue();
    }

    private String 오류_응답(int code, String status, String errorCode) {
        return """
                {"error":{"code":%d,"status":"%s","details":[
                  {"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"%s"}]}}
                """.formatted(code, status, errorCode);
    }
}
