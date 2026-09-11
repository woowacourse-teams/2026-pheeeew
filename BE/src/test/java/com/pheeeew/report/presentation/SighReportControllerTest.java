package com.pheeeew.report.presentation;

import static com.pheeeew.report.fixture.SighReportFixture.기본_신고_사유;
import static com.pheeeew.report.fixture.SighReportFixture.신고_사유;
import static com.pheeeew.report.fixture.SighReportFixture.신고자_기기_공개_식별자;
import static com.pheeeew.report.fixture.SighReportFixture.저장된_기본_신고;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.report.application.SighReportResult;
import com.pheeeew.report.application.SighReportService;
import com.pheeeew.report.exception.SighReportErrorCode;
import com.pheeeew.report.exception.SighReportException;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(SighReportController.class)
class SighReportControllerTest {

    private static final String REPORTS_URI = "/api/v2/reports";
    private static final Long SIGH_ID = 42L;
    private static final Long REPORT_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T02:44:00Z");

    private final RestTestClient client;

    @MockitoBean
    private SighReportService sighReportService;

    @Autowired
    SighReportControllerTest(RestTestClient client) {
        this.client = client;
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(AccessTokenFixture.인증된_기기(신고자_기기_공개_식별자()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 한숨을_최초로_신고하면_201과_신고_정보를_반환한다() {
        // given
        when(sighReportService.save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유()))
                .thenReturn(SighReportResult.of(저장된_기본_신고(REPORT_ID, CREATED_AT), true));

        // when
        RestTestClient.ResponseSpec result = 신고한다(기본_신고_본문());

        // then
        result.expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json(기본_신고_응답(), JsonCompareMode.STRICT);
        verify(sighReportService).save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유());
    }

    @Test
    void 이미_신고한_한숨을_다시_신고하면_200과_최초_신고를_반환한다() {
        // given
        String 다시_보낸_사유 = "나중에 바꾼 사유입니다";
        when(sighReportService.save(SIGH_ID, 신고자_기기_공개_식별자(), 다시_보낸_사유))
                .thenReturn(SighReportResult.of(저장된_기본_신고(REPORT_ID, CREATED_AT), false));

        // when
        RestTestClient.ResponseSpec result = 신고한다("""
                {"sighId": 42, "reason": "나중에 바꾼 사유입니다"}
                """);

        // then
        result.expectStatus().isOk()
                .expectBody()
                .json(기본_신고_응답(), JsonCompareMode.STRICT);
        verify(sighReportService).save(SIGH_ID, 신고자_기기_공개_식별자(), 다시_보낸_사유);
    }

    @Test
    void 요청_본문에_담긴_기기_식별자는_무시하고_인증된_기기를_신고자로_쓴다() {
        // given
        UUID 사칭하려는_기기 = UUID.fromString("00000000-0000-4000-8000-000000009999");
        when(sighReportService.save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유()))
                .thenReturn(SighReportResult.of(저장된_기본_신고(REPORT_ID, CREATED_AT), true));

        // when
        RestTestClient.ResponseSpec result = 신고한다("""
                {"sighId": 42, "deviceId": "%s", "reason": "광고성 게시물입니다"}
                """.formatted(사칭하려는_기기));

        // then
        result.expectStatus().isCreated();
        verify(sighReportService).save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유());
    }

    @Test
    void 인증_컨텍스트가_없으면_신고자를_비운_채_저장하지_않고_500을_반환한다() {
        // given
        SecurityContextHolder.clearContext();

        // when
        RestTestClient.ResponseSpec result = 신고한다(기본_신고_본문());

        // then
        오류를_검증한다(result, 500, "COMMON-002", "서버 내부 오류가 발생했습니다.");
        verifyNoInteractions(sighReportService);
    }

    @ParameterizedTest
    @MethodSource("올바르지_않은_요청_본문들")
    void 한숨_식별자나_신고_사유가_올바르지_않으면_400을_반환한다(String body) {
        // given / when
        RestTestClient.ResponseSpec result = 신고한다(body);

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(sighReportService);
    }

    @Test
    void 요청_본문이_없으면_400을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri(REPORTS_URI)
                .exchange();

        // then
        오류를_검증한다(result, 400, "COMMON-001", "요청 값이 올바르지 않습니다.");
        verifyNoInteractions(sighReportService);
    }

    @Test
    void 신고할_한숨이_없으면_404를_반환한다() {
        // given
        when(sighReportService.save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유()))
                .thenThrow(new SighException(SighErrorCode.SIGH_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 신고한다(기본_신고_본문());

        // then
        오류를_검증한다(result, 404, "SIGH-002", "한숨을 찾을 수 없습니다.");
    }

    @Test
    void 신고_도메인_예외는_정의된_상태와_코드로_반환한다() {
        // given
        when(sighReportService.save(SIGH_ID, 신고자_기기_공개_식별자(), 기본_신고_사유()))
                .thenThrow(new SighReportException(
                        SighReportErrorCode.SIGH_REPORT_SAVE_FAILED,
                        new IllegalStateException()
                ));

        // when
        RestTestClient.ResponseSpec result = 신고한다(기본_신고_본문());

        // then
        오류를_검증한다(result, 500, "REPORT-001", "신고를 저장하지 못했습니다.");
    }

    private RestTestClient.ResponseSpec 신고한다(String body) {
        return client.post()
                .uri(REPORTS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void 오류를_검증한다(
            RestTestClient.ResponseSpec result,
            int status,
            String code,
            String message
    ) {
        result.expectStatus().isEqualTo(status)
                .expectBody()
                .json("""
                        {"code":"%s","message":"%s"}
                        """.formatted(code, message), JsonCompareMode.STRICT);
    }

    private String 기본_신고_본문() {
        return """
                {"sighId": 42, "reason": "광고성 게시물입니다"}
                """;
    }

    private String 기본_신고_응답() {
        return """
                {
                  "id": 7,
                  "sighId": 42,
                  "reason": "광고성 게시물입니다",
                  "createdAt": "2026-09-01T02:44:00Z"
                }
                """;
    }

    private static Stream<String> 올바르지_않은_요청_본문들() {
        return Stream.of(
                """
                        {"reason": "광고성 게시물입니다"}
                        """,
                """
                        {"sighId": 0, "reason": "광고성 게시물입니다"}
                        """,
                """
                        {"sighId": "마흔둘", "reason": "광고성 게시물입니다"}
                        """,
                """
                        {"sighId": 42}
                        """,
                """
                        {"sighId": 42, "reason": ""}
                        """,
                """
                        {"sighId": 42, "reason": "   "}
                        """,
                """
                        {"sighId": 42, "reason": "%s"}
                        """.formatted(신고_사유(201))
        );
    }
}
