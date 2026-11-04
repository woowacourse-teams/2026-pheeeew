package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MapReadDiagnosticsTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MapReadDiagnostics.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
        logs.stop();
    }

    @Test
    void 느린_표본과_무작위_표본을_합쳐_상한을_적용하고_요청_민감값을_출력하지_않는다() {
        // given
        var registry = new SimpleMeterRegistry();
        var now = new AtomicLong();
        var diagnostics = new MapReadDiagnostics(true, 1, 100, 2, registry, () -> 0, now::get);
        var request = new MockHttpServletRequest("GET", "/api/v4/emotions/map");
        request.setQueryString("cursor=secret-cursor&minLongitude=126.123456&deviceId=private-device");
        request.addHeader("Authorization", "Bearer secret-token");
        var response = new MockHttpServletResponse();
        response.setHeader("Content-Length", "23");

        // when
        for (int index = 0; index < 3; index++) {
            try (var observation = MapReadObservation.open(request, registry, now::get)) {
                now.addAndGet(TimeUnit.MILLISECONDS.toNanos(index == 0 ? 110 : 10));
                observation.finish(200, false);
                diagnostics.write(observation, response, false);
            }
        }

        // then
        assertThat(logs.list).hasSize(2);
        assertThat(registry.get("pheeeew.map.diagnostic.logs").tag("result", "sampled").counter().count()).isEqualTo(2);
        assertThat(registry.get("pheeeew.map.diagnostic.logs").tag("result", "suppressed").counter().count()).isEqualTo(1);
        String fields = logs.list.stream().map(event -> event.getKeyValuePairs().toString()).toList().toString();
        assertThat(fields).contains("map_read_sample", "slow", "random", "responseContentLength")
                .doesNotContain("secret-cursor", "126.123456", "private-device", "secret-token", "Authorization");

        // when: 다음 단조시계 구간에는 다시 표본을 허용한다.
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        try (var observation = MapReadObservation.open(request, registry, now::get)) {
            observation.finish(200, false);
            diagnostics.write(observation, response, false);
        }
        assertThat(logs.list).hasSize(3);
    }

    @Test
    void 비활성_진단과_선정되지_않은_빠른_요청은_로그를_남기지_않는다() {
        // given
        var registry = new SimpleMeterRegistry();
        var now = new AtomicLong();
        var disabled = new MapReadDiagnostics(false, 1, 0, 5, registry, () -> 0, now::get);
        var unsampled = new MapReadDiagnostics(true, 0, 100, 5, registry, () -> 1, now::get);

        // when
        try (var observation = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), registry, now::get)) {
            now.set(TimeUnit.MILLISECONDS.toNanos(10));
            observation.finish(200, false);
            disabled.write(observation, new MockHttpServletResponse(), false);
            unsampled.write(observation, new MockHttpServletResponse(), false);
        }

        // then
        assertThat(logs.list).isEmpty();
        assertThat(registry.find("pheeeew.map.diagnostic.logs").counters()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 출력_실패는_관측한_응답_상태와_커밋_여부를_보존하고_처리_실패로_분류한다(boolean committed) {
        // given
        var registry = new SimpleMeterRegistry();
        var diagnostics = new MapReadDiagnostics(true, 1, 0, 5, registry);
        var filter = new MapReadObservationFilter(registry, new MapReadMetrics(registry), diagnostics);
        var request = new MockHttpServletRequest("GET", "/api/v4/emotions/map");
        var response = new MockHttpServletResponse();
        var original = new IOException("output failed");

        // when
        assertThatThrownBy(() -> filter.doFilter(request, response, (incoming, outgoing) -> {
            if (committed) {
                outgoing.getWriter().write("partial response");
                outgoing.flushBuffer();
            }
            throw original;
        })).isSameAs(original);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.isCommitted()).isEqualTo(committed);
        assertThat(logs.list).hasSize(1);
        var fields = logs.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields).containsEntry("observedStatus", 200)
                .containsEntry("unhandledFailure", true)
                .containsEntry("responseCommitted", committed)
                .containsEntry("outcome", "server_error")
                .doesNotContainKey("status");
        assertThat(registry.get("pheeeew.map.read.requests").tag("outcome", "server_error").counter().count()).isEqualTo(1);
        assertThat(MapReadObservation.active()).isFalse();
    }
}
