package com.pheeeew.notification.infra;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static com.pheeeew.notification.fixture.PushTokenFixture.기본_등록_값;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nimbusds.jwt.SignedJWT;
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.web.client.HttpClientErrorException;

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

    @Test
    void 무효_등록_값이_아닌_오류는_무효로_보지_않고_예외를_던진다() {
        // given
        가짜_구글.복호화_응답을_넣는다(404, "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}");
        가짜_구글.복호화_응답을_넣는다(400, 오류_응답(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT"));

        // when
        Throwable 프로젝트_없음 = catchThrowable(() -> sender.send(기본_등록_값, 메시지));
        Throwable 잘못된_요청 = catchThrowable(() -> sender.send(기본_등록_값, 메시지));

        // then
        assertThat(프로젝트_없음).isInstanceOf(HttpClientErrorException.class);
        assertThat(잘못된_요청).isInstanceOf(HttpClientErrorException.class);
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
