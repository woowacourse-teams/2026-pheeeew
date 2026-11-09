package com.pheeeew.emotion.application.query;

import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 지도 조회 1건을 HTTP 관측 문맥 안에서 실행하고, 운영 지표 pheeeew.map.read.requests와 같은 결정값을 돌려준다.
 * 캐시 결과는 cache_hit(적중)·cache_load(적재)·cache_fallback(SQL 대체)로 나뉜다.
 */
final class MapReadDecisions {

    private MapReadDecisions() {
    }

    static <T> Decided<T> pins(MeterRegistry meters, Supplier<T> query) {
        return run(meters, "/api/v4/emotions/map", query);
    }

    static <T> Decided<T> regions(MeterRegistry meters, Supplier<T> query) {
        return run(meters, "/api/v4/emotions/map/regions", query);
    }

    private static <T> Decided<T> run(MeterRegistry meters, String path, Supplier<T> query) {
        try (var observation = MapReadObservation.open(new MockHttpServletRequest("GET", path), meters)) {
            T value = query.get();
            observation.finish(200, false);
            return Decided.of(value, (String) observation.details().get("decision"));
        }
    }

    record Decided<T>(T value, String decision) {

        private static <T> Decided<T> of(T value, String decision) {
            return new Decided<>(value, decision);
        }

        /** 캐시가 응답했으면(적중 또는 적재) true다. */
        boolean cached() {
            return "cache_hit".equals(decision) || "cache_load".equals(decision);
        }
    }
}
