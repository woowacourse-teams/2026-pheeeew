package com.pheeeew.common.logging;

import static com.pheeeew.common.exception.CommonErrorCode.INTERNAL_SERVER_ERROR;
import static com.pheeeew.common.exception.CommonErrorCode.INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.common.exception.PheeeewException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogWriter.class);
    private final List<String> output = new ArrayList<>();
    private final LoggerContext encoderContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();
    private final AtomicLong ticks = new AtomicLong();
    private long durationNanos;
    private final RequestLoggingFilter filter = new RequestLoggingFilter(() -> ticks.getAndAdd(durationNanos));
    private final TestController controller = new TestController();
    private AppenderBase<ILoggingEvent> appender;
    private MockMvc client;

    @BeforeEach
    void setUp() {
        encoderContext.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(encoderContext);
        encoder.setFormat("logstash");
        encoder.start();
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                output.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
            }
        };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        client = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        encoder.stop();
        encoderContext.stop();
        MDC.remove("correlationId");
    }

    @Test
    void 정상_요청은_로그_없이_새_추적번호를_응답하고_기존_MDC를_복원한다() throws Exception {
        // given
        MDC.put("correlationId", "outer-context");

        // when
        MvcResult first = client.perform(get("/test/ok").header("X-Correlation-ID", "client-controlled"))
                .andExpect(status().isOk()).andReturn();
        MvcResult second = client.perform(get("/test/ok")).andReturn();

        // then
        String id = first.getResponse().getHeader("X-Correlation-ID");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(first.getResponse().getContentAsString()).isEqualTo(id);
        assertThat(second.getResponse().getHeader("X-Correlation-ID")).isNotEqualTo(id);
        assertThat(MDC.get("correlationId")).isEqualTo("outer-context");
        assertThat(output).isEmpty();
        assertThat(first.getRequest().getAttribute(RequestTiming.ATTRIBUTE)).isNull();
        assertThat(second.getRequest().getAttribute(RequestTiming.ATTRIBUTE)).isNull();
        assertThat(controller.timings).hasSize(2).allSatisfy(snapshot -> assertThat(snapshot)
                .containsExactlyEntriesOf(Map.of(RequestTiming.Stage.PRESS_INCREASE, RequestTiming.Measurement.of(1, 10, 10))));
    }

    @Test
    void 서버_오류는_요청값_없이_경로_템플릿과_발생_위치와_예외_메시지를_기록한다() throws Exception {
        // given
        String sensitive = "private-location-memo-token";

        // when
        MvcResult response = client.perform(post("/test/fail/" + sensitive)
                        .queryParam("latitude", "37.1234567")
                        .header("Authorization", "Bearer " + sensitive)
                        .header("Cookie", "session=" + sensitive)
                        .header("X-Correlation-ID", sensitive)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"" + sensitive + "\"}"))
                .andExpect(status().isInternalServerError()).andReturn();

        // then
        assertThat(output).hasSize(1);
        String json = output.getFirst();
        Map<String, Object> event = JsonParserFactory.getJsonParser().parseMap(json);
        assertThat(event).containsEntry("event", "http_request_failed")
                .containsEntry("route", "/test/fail/{id}")
                .containsEntry("method", "POST")
                .containsEntry("level", "ERROR")
                .containsEntry("errorCode", "COMMON-002")
                .containsEntry("exceptionType", "IllegalArgumentException")
                .containsEntry("errorMessages", List.of(
                        "java.lang.IllegalStateException: 집계 상태가 초기화되지 않았습니다.",
                        "java.lang.IllegalArgumentException: 날짜 순서가 올바르지 않습니다."))
                .containsEntry("correlationId", response.getResponse().getHeader("X-Correlation-ID"))
                .doesNotContainKeys("params", "sqlState", "timings");
        assertThat(((Number) event.get("status")).intValue()).isEqualTo(500);
        assertThat(event.get("origin").toString()).matches("RequestLoggingFilterTest\\$TestController\\.fail:\\d+");
        assertThat(event.get("errorStack").toString())
                .contains("IllegalStateException", "IllegalArgumentException", "TestController.fail");
        assertThat(json).doesNotContain(sensitive, "37.1234567", "secret-suppressed", "stack_trace");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void 서비스의_5xx_예외도_한_번_기록하지만_예상된_4xx는_기록하지_않는다() throws Exception {
        // given / when
        MvcResult failed = client.perform(get("/test/service-failure")).andExpect(status().isInternalServerError()).andReturn();
        client.perform(get("/test/invalid")).andExpect(status().isBadRequest());

        // then
        assertThat(output).hasSize(1);
        assertThat(output.getFirst()).contains("COMMON-002", "PheeeewException", "service-cause")
                .doesNotContain("invalid-cause");
        assertThat(failed.getRequest().getAttribute(RequestTiming.ATTRIBUTE)).isNull();
        assertThat(controller.timings).singleElement().satisfies(snapshot -> assertThat(snapshot)
                .containsEntry(RequestTiming.Stage.PRESS_INCREASE, RequestTiming.Measurement.of(1, 10, 10)));
    }

    @Test
    void 일초_미만은_생략하고_일초부터_느린_요청을_기록한다() throws Exception {
        // given
        durationNanos = 999_000_000;
        client.perform(get("/test/ok"));
        assertThat(output).isEmpty();

        // when
        durationNanos = 1_000_000_000;
        client.perform(get("/test/ok"));

        // then
        assertThat(output).hasSize(1);
        Map<String, Object> event = JsonParserFactory.getJsonParser().parseMap(output.getFirst());
        assertThat(event).containsEntry("event", "http_request_slow").containsEntry("level", "WARN");
        assertThat(((Number) event.get("durationMs")).longValue()).isEqualTo(1000);
    }

    @Test
    void 느린_서버_오류는_오류_로그만_남긴다() throws Exception {
        // given
        durationNanos = 2_000_000_000;

        // when
        client.perform(get("/test/service-failure"));

        // then
        assertThat(output).hasSize(1);
        assertThat(output.getFirst()).contains("http_request_failed").doesNotContain("http_request_slow");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 느린_요청과_서버_오류에_측정한_단계만_호출_수와_밀리초로_출력한다(boolean failed) throws Exception {
        // given
        durationNanos = 1_000_000_000;
        // when
        MvcResult result = client.perform(get("/test/timings/" + failed))
                .andExpect(failed ? status().isInternalServerError() : status().isOk()).andReturn();
        // then
        assertThat(output).hasSize(1);
        Map<String, Object> event = JsonParserFactory.getJsonParser().parseMap(output.getFirst());
        assertThat(event).containsEntry("event", failed ? "http_request_failed" : "http_request_slow")
                .containsEntry("correlationId", result.getResponse().getHeader("X-Correlation-ID"));
        Map<?, ?> timings = (Map<?, ?>) event.get("timings");
        assertThat(timings).hasSize(3);
        assertTiming(timings.get("regions_intersection"), 2, 4.0, 2.75);
        assertTiming(timings.get("press_increase"), 2, 0.75, 0.5);
        assertTiming(timings.get("regions_service"), 1, 10.0, 10.0);
        assertThat(result.getRequest().getAttribute(RequestTiming.ATTRIBUTE)).isNull();
    }

    @Test
    void 미처리_예외의_HTTP_로그는_요청_경로와_메서드_원문_없이_남고_전파_후_MDC를_정리한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("PRIVATE-METHOD", "/private-path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IOException failure = new IOException("unhandled-failure");
        failure.initCause(new IllegalStateException("unhandled-cause", failure));

        // when / then
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            try {
                RequestTiming.record(RequestTiming.Stage.PRESS_INCREASE, 1_500_000);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
            throw failure;
        }))
                .isSameAs(failure);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst()).contains("UNMAPPED", "OTHER", "IOException", "unhandled-failure", "unhandled-cause")
                .doesNotContain("PRIVATE-METHOD", "private-path", "sqlState");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(request.getAttribute(RequestTiming.ATTRIBUTE)).isNull();
        Map<?, ?> timings = (Map<?, ?>) JsonParserFactory.getJsonParser().parseMap(output.getFirst()).get("timings");
        assertTiming(timings.get("press_increase"), 1, 1.5, 1.5);
    }

    @Test
    void 서버_오류에는_허용한_이름의_경로와_쿼리_값만_실린다() throws Exception {
        // given
        String groupId = "0b0e6f0a-1c2d-4e3f-8a9b-7c6d5e4f3a2b";
        String requestId = "11111111-2222-4333-8444-555555555555";

        // when
        client.perform(post("/test/values/" + groupId + "/HEART/PATH-INVITE-CODE")
                        .queryParam("weeksAgo", "1")
                        .queryParam("blockId", "7")
                        .queryParam("latitude", "37.1234567")
                        .queryParam("cursor", "CURSOR-QUERY")
                        .queryParam("inviteCode", "INVITE-QUERY")
                        .queryParam("memo", "MEMO-QUERY")
                        .queryParam("refreshToken", "REFRESH-QUERY")
                        .queryParam("requestId", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"BODY-STATE\",\"memo\":\"MEMO-BODY\"}"))
                .andExpect(status().isInternalServerError());

        // then
        assertThat(output).hasSize(1);
        assertThat(JsonParserFactory.getJsonParser().parseMap(output.getFirst())).containsEntry("params",
                Map.of("groupId", groupId, "emojiType", "HEART", "weeksAgo", "1", "blockId", "7"));
        assertThat(output.getFirst()).doesNotContain("PATH-INVITE-CODE", "37.1234567", "CURSOR-QUERY", "INVITE-QUERY",
                "MEMO-QUERY", "REFRESH-QUERY", requestId, "BODY-STATE", "MEMO-BODY");
    }

    @Test
    void 허용한_이름이어도_형식에_맞지_않는_값은_실리지_않는다() throws Exception {
        // given
        String tooLong = "a".repeat(37);

        // when
        client.perform(post("/test/values/" + tooLong + "/bad.shape/CODE")
                        .queryParam("weeksAgo", "37.1234567")
                        .queryParam("state", "free text with spaces")
                        .queryParam("platform", "한글값")
                        .queryParam("blockId", "12\n34"))
                .andExpect(status().isInternalServerError());

        // then
        assertThat(output).hasSize(1);
        assertThat(JsonParserFactory.getJsonParser().parseMap(output.getFirst())).doesNotContainKey("params");
        assertThat(output.getFirst())
                .doesNotContain(tooLong, "bad.shape", "37.1234567", "free text with spaces", "한글값");
    }

    @Test
    void 허용한_이름이어도_요청_본문으로_온_값은_실리지_않는다() throws Exception {
        // given
        String groupId = "0b0e6f0a-1c2d-4e3f-8a9b-7c6d5e4f3a2b";

        // when
        client.perform(post("/test/values/" + groupId + "/HEART/CODE")
                        .queryParam("weeksAgo", "1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("state=BODY-STATE&platform=BODY-PLATFORM&blockId=7"))
                .andExpect(status().isInternalServerError());

        // then
        assertThat(output).hasSize(1);
        assertThat(JsonParserFactory.getJsonParser().parseMap(output.getFirst())).containsEntry("params",
                Map.of("groupId", groupId, "emojiType", "HEART", "weeksAgo", "1"));
        assertThat(output.getFirst()).doesNotContain("BODY-STATE", "BODY-PLATFORM");
    }

    @Test
    void 지원하지_않는_요청_형식은_헤더_값이_든_오류_로그를_남기지_않는다() throws Exception {
        // given
        String contentType = "text/plain;note=\"HEADER-SECRET\"";

        // when
        client.perform(post("/test/body").header("Content-Type", contentType).content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        // then
        assertThat(output).isEmpty();
    }

    @Test
    void DB_원인이면_감싼_예외의_메시지까지_모두_버리고_SQL_상태만_남긴다() throws Exception {
        // given
        controller.failure = new PheeeewException(INTERNAL_SERVER_ERROR, new IllegalStateException(
                "could not execute statement [wrapper-secret]", new SQLException("Key (memo)=(row-secret)", "23505")));

        // when
        client.perform(get("/test/raise")).andExpect(status().isInternalServerError());

        // then
        assertThat(output).hasSize(1);
        assertThat(JsonParserFactory.getJsonParser().parseMap(output.getFirst()))
                .containsEntry("sqlState", "23505")
                .containsEntry("exceptionType", "SQLException")
                .doesNotContainKey("errorMessages");
        assertThat(output.getFirst())
                .doesNotContain("wrapper-secret", "row-secret", "could not execute", "서버 내부 오류");
    }

    @Test
    void 로그를_만들다_실패해도_응답과_예외_전파는_그대로이고_건너뜀_경고만_남는다() throws Exception {
        // given
        controller.failure = new IllegalStateException("outer",
                new MessageFailingException(new IllegalArgumentException("inner")));
        RuntimeException unhandled = new MessageFailingException(null);

        // when
        MvcResult handled = client.perform(get("/test/raise"))
                .andExpect(status().isInternalServerError()).andReturn();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/test"),
                new MockHttpServletResponse(), (req, res) -> { throw unhandled; })).isSameAs(unhandled);

        // then
        assertThat(handled.getResponse().getContentAsString()).contains("COMMON-002");
        assertThat(output).hasSize(2).allSatisfy(line -> {
            assertThat(JsonParserFactory.getJsonParser().parseMap(line))
                    .containsEntry("event", "http_request_log_skipped")
                    .containsEntry("level", "WARN")
                    .containsEntry("skippedBy", "java.lang.UnsupportedOperationException");
            assertThat(line).doesNotContain("http_request_failed", "message-read-secret");
        });
        assertThat(MDC.get("correlationId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 정상과_예외_종료에서_기존_시간_수집_속성과_MDC를_복원한다(boolean failed) throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        Object previous = new Object();
        request.setAttribute(RequestTiming.ATTRIBUTE, previous);
        MDC.put("correlationId", "outer");
        IOException failure = new IOException("failure");
        FilterChain chain = (req, res) -> {
            assertThat(req.getAttribute(RequestTiming.ATTRIBUTE)).isInstanceOf(RequestTiming.class).isNotSameAs(previous);
            if (failed) {
                throw failure;
            }
        };
        // when / then
        if (failed) {
            assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain)).isSameAs(failure);
        } else {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }
        assertThat(request.getAttribute(RequestTiming.ATTRIBUTE)).isSameAs(previous);
        assertThat(MDC.get("correlationId")).isEqualTo("outer");
    }

    private void assertTiming(Object value, long count, double totalMs, double maxMs) {
        Map<?, ?> timing = (Map<?, ?>) value;
        assertThat(timing).hasSize(3);
        assertThat(((Number) timing.get("count")).longValue()).isEqualTo(count);
        assertThat(((Number) timing.get("totalMs")).doubleValue()).isEqualTo(totalMs);
        assertThat(((Number) timing.get("maxMs")).doubleValue()).isEqualTo(maxMs);
    }

    @RestController
    static class TestController {

        private Exception failure;
        private final List<Map<RequestTiming.Stage, RequestTiming.Measurement>> timings = new ArrayList<>();

        @GetMapping("/test/ok")
        String ok(HttpServletRequest request) {
            captureTiming(request);
            return MDC.get("correlationId");
        }

        @PostMapping("/test/fail/{id}")
        void fail() {
            IllegalStateException failure = new IllegalStateException("집계 상태가 초기화되지 않았습니다.",
                    new IllegalArgumentException("날짜 순서가 올바르지 않습니다."));
            failure.addSuppressed(new IllegalStateException("secret-suppressed"));
            throw failure;
        }

        @GetMapping("/test/service-failure")
        void serviceFailure(HttpServletRequest request) {
            captureTiming(request);
            throw new PheeeewException(INTERNAL_SERVER_ERROR, new IllegalStateException("service-cause"));
        }

        @GetMapping("/test/timings/{failed}")
        void timed(@PathVariable boolean failed) {
            RequestTiming.record(RequestTiming.Stage.REGIONS_INTERSECTION, 1_250_000);
            RequestTiming.record(RequestTiming.Stage.REGIONS_INTERSECTION, 2_750_000);
            RequestTiming.record(RequestTiming.Stage.PRESS_INCREASE, 500_000);
            RequestTiming.record(RequestTiming.Stage.PRESS_INCREASE, 250_000);
            RequestTiming.record(RequestTiming.Stage.REGIONS_SERVICE, 10_000_000);
            if (failed) {
                throw new PheeeewException(INTERNAL_SERVER_ERROR, new IllegalStateException("timed failure"));
            }
        }

        @GetMapping("/test/invalid")
        void invalid() {
            throw new PheeeewException(INVALID_REQUEST, new IllegalArgumentException("invalid-cause"));
        }

        @GetMapping("/test/raise")
        void raise() throws Exception {
            throw failure;
        }

        @PostMapping("/test/values/{groupId}/{emojiType}/{inviteCode}")
        void values() {
            throw new IllegalStateException("values-failure");
        }

        @PostMapping("/test/body")
        void body(
                @RequestBody Map<String, Object> body
        ) {
        }

        private void captureTiming(HttpServletRequest request) {
            RequestTiming.record(RequestTiming.Stage.PRESS_INCREASE, 10);
            timings.add(RequestTiming.snapshot(request));
        }
    }

    static class MessageFailingException extends RuntimeException {

        MessageFailingException(Throwable cause) {
            super("original-message", cause);
        }

        @Override
        public String getMessage() {
            throw new UnsupportedOperationException("message-read-secret");
        }
    }
}
