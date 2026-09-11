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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogWriter.class);
    private final List<String> output = new ArrayList<>();
    private final LoggerContext encoderContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();
    private final AtomicLong ticks = new AtomicLong();
    private long durationNanos;
    private final RequestLoggingFilter filter = new RequestLoggingFilter(() -> ticks.getAndAdd(durationNanos));
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
        client = MockMvcBuilders.standaloneSetup(new TestController())
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
    }

    @Test
    void 서버_오류는_요청값과_예외_원문_없이_경로_템플릿과_발생_위치를_기록한다() throws Exception {
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
                .containsEntry("correlationId", response.getResponse().getHeader("X-Correlation-ID"));
        assertThat(((Number) event.get("status")).intValue()).isEqualTo(500);
        assertThat(event.get("errorStack").toString())
                .contains("IllegalStateException", "IllegalArgumentException", "TestController.fail");
        assertThat(json).doesNotContain(sensitive, "37.1234567", "secret-cause", "secret-suppressed", "stack_trace");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void 서비스의_5xx_예외도_한_번_기록하지만_예상된_4xx는_기록하지_않는다() throws Exception {
        // given / when
        client.perform(get("/test/service-failure")).andExpect(status().isInternalServerError());
        client.perform(get("/test/invalid")).andExpect(status().isBadRequest());

        // then
        assertThat(output).hasSize(1);
        assertThat(output.getFirst()).contains("COMMON-002", "PheeeewException")
                .doesNotContain("secret-cause");
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

    @Test
    void 미처리_예외의_HTTP_로그는_원문을_제외하고_전파_후_MDC를_정리한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("PRIVATE-METHOD", "/private-path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IOException failure = new IOException("private-exception");
        failure.initCause(new IllegalStateException("private-cause", failure));

        // when / then
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> { throw failure; }))
                .isSameAs(failure);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst()).contains("UNMAPPED", "OTHER", "IOException")
                .doesNotContain("PRIVATE-METHOD", "private-path", "private-exception", "private-cause");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        String ok() {
            return MDC.get("correlationId");
        }

        @PostMapping("/test/fail/{id}")
        void fail() {
            IllegalStateException failure = new IllegalStateException("private-location-memo-token",
                    new IllegalArgumentException("secret-cause"));
            failure.addSuppressed(new IllegalStateException("secret-suppressed"));
            throw failure;
        }

        @GetMapping("/test/service-failure")
        void serviceFailure() {
            throw new PheeeewException(INTERNAL_SERVER_ERROR, new IllegalStateException("secret-cause"));
        }

        @GetMapping("/test/invalid")
        void invalid() {
            throw new PheeeewException(INVALID_REQUEST, new IllegalArgumentException("secret-cause"));
        }
    }
}
