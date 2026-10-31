package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.common.logging.RequestTiming;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MapReadObservationTest {

    @Test
    void 같은_요청의_경계만_계산하고_없는_구간은_0으로_만들지_않는다() {
        // given
        var registry = new SimpleMeterRegistry();
        var request = new MockHttpServletRequest("GET", "/api/v4/emotions/map");
        var clock = new AtomicLong();
        Object previous = RequestTiming.begin(request);
        try (var observation = MapReadObservation.open(request, registry, clock::get)) {
            // when
            clock.set(10_000_000);
            MapReadObservation.mvcStarted();
            clock.set(13_000_000);
            MapReadObservation.controllerStarted();
            clock.set(20_000_000);
            MapReadObservation.controllerEnded();
            clock.set(25_000_000);
            MapReadObservation.mvcEnded();
            clock.set(27_000_000);
            observation.finish(200, false);
            observation.finish(200, false);

            // then
            assertTime(registry, "map_filter_total", 27);
            assertTime(registry, "map_pre_mvc", 10);
            assertTime(registry, "map_mvc_pre_controller", 3);
            assertTime(registry, "map_response_processing", 5);
            assertTime(registry, "map_filter_return", 2);
            assertThat(registry.find("pheeeew.map.read.stage").tag("stage", "map_json_write").timer()).isNull();
            assertThat(registry.get("pheeeew.map.read.requests").counter().count()).isEqualTo(1);
            assertThat(observation.details().get("timings")).asString().contains("map_pre_mvc");
        } finally {
            RequestTiming.restore(request, previous);
        }
        assertThat(MapReadObservation.active()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "2024-02-29,2024-03-31,2024-03-31T03:00:00Z,month_current_default",
            "2024-02-29,2024-03-31,2024-03-31T15:00:00Z,month_other_default",
            "2025-02-28,2025-03-31,2025-03-31T00:00:00Z,month_current_default",
            "2026-09-30,2026-10-30,2026-10-29T15:00:00Z,month_current_default",
            "2026-10-01,2026-10-30,2026-10-30T00:00:00Z,other"
    })
    void 월말과_KST_자정에도_달력_한달_조건을_분류한다(LocalDate start, LocalDate end, Instant now, String expected) {
        // given
        var registry = new SimpleMeterRegistry();
        try (var observation = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), registry)) {
            // when
            MapReadObservation.query(start, end, false, true, Clock.fixed(now, ZoneOffset.UTC));
            observation.finish(200, false);

            // then
            assertThat(observation.details()).containsEntry("query", expected).containsEntry("page", "next");
        }
    }

    @Test
    void 예외와_중첩_요청에서도_이전_문맥을_복원하고_실패를_성공으로_세지_않는다() {
        // given
        var registry = new SimpleMeterRegistry();
        try (var outer = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v2/emotions/map"), registry)) {
            // when
            assertThatThrownBy(() -> {
                try (var inner = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), registry)) {
                    inner.finish(200, true);
                    throw new IllegalStateException("original");
                }
            }).isInstanceOf(IllegalStateException.class).hasMessage("original");

            // then
            assertThat(MapReadObservation.apiVersion()).isEqualTo("v2");
            assertThat(registry.get("pheeeew.map.read.requests").tags("api_version", "v4", "outcome", "server_error",
                    "query", "unclassified").counter().count()).isEqualTo(1);
        }
        assertThat(MapReadObservation.active()).isFalse();
    }

    @Test
    void 초기화_계측_실패와_비지도_요청은_원래_필터_처리를_유지한다() throws Exception {
        // given
        var registry = new SimpleMeterRegistry();
        var diagnostics = new MapReadDiagnostics(false, 0, 100, 5, registry);
        var filter = new MapReadObservationFilter(registry, new MapReadMetrics(registry), diagnostics,
                () -> { throw new IllegalStateException("clock unavailable"); });
        var called = new AtomicLong();

        // when
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), new MockHttpServletResponse(),
                (request, response) -> called.incrementAndGet());
        filter.doFilter(new MockHttpServletRequest("GET", "/other"), new MockHttpServletResponse(),
                (request, response) -> called.incrementAndGet());

        // then
        assertThat(called.get()).isEqualTo(2);
        assertThat(MapReadObservation.active()).isFalse();
        assertThat(registry.find("pheeeew.map.read.requests").counters()).isEmpty();
    }

    @Test
    void 출력_예외를_그대로_전파하고_필터의_이전_문맥을_복원한다() throws Exception {
        // given
        var registry = new SimpleMeterRegistry();
        var filter = new MapReadObservationFilter(registry, new MapReadMetrics(registry),
                new MapReadDiagnostics(false, 0, 100, 5, registry));
        var original = new IOException("output failed");
        var called = new AtomicLong();

        // when / then
        try (var outer = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v2/emotions/map"), registry)) {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/v4/emotions/map"),
                    new MockHttpServletResponse(), (request, response) -> {
                        called.incrementAndGet();
                        throw original;
                    })).isSameAs(original);
            assertThat(MapReadObservation.apiVersion()).isEqualTo("v2");
        }
        assertThat(MapReadObservation.active()).isFalse();
        assertThat(registry.get("pheeeew.map.read.requests").tags("api_version", "v4", "outcome", "server_error")
                .counter().count()).isEqualTo(1);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), new MockHttpServletResponse(),
                (request, response) -> called.incrementAndGet());
        assertThat(called.get()).isEqualTo(2);
        assertThat(MapReadObservation.active()).isFalse();
        assertThat(registry.get("pheeeew.map.read.requests").tags("api_version", "v4", "outcome", "success")
                .counter().count()).isEqualTo(1);
    }

    private void assertTime(SimpleMeterRegistry registry, String stage, double millis) {
        Timer timer = registry.get("pheeeew.map.read.stage").tags("api_version", "v4", "stage", stage).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(millis);
    }
}
