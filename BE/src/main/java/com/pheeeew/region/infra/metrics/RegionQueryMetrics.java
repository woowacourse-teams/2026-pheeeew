package com.pheeeew.region.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RegionQueryMetrics {

    private final Map<Operation, Timer> successes = new EnumMap<>(Operation.class);
    private final Map<Operation, Timer> errors = new EnumMap<>(Operation.class);

    public RegionQueryMetrics(MeterRegistry registry) {
        for (Operation operation : Operation.values()) {
            successes.put(operation, registerQuery(registry, operation, "success"));
            errors.put(operation, registerQuery(registry, operation, "error"));
        }
    }

    public void recordQuery(Operation operation, long durationNanos, boolean succeeded) {
        (succeeded ? successes : errors).get(operation).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Timer registerQuery(MeterRegistry registry, Operation operation, String outcome) {
        return Timer.builder("pheeeew.region.query")
                .description("Region repository operation duration including JDBC communication and result mapping")
                .tags("operation", operation.name().toLowerCase(Locale.ROOT), "outcome", outcome)
                // 운영에서 관측한 분 단위 지연도 구분하는 진단용 경계이며, 합의된 SLO는 아니다.
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(500),
                        Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10),
                        Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120), Duration.ofSeconds(300))
                .register(registry);
    }

    public enum Operation {
        INTERSECTING_REGIONS,
        BOUNDARIES_VERIFIED,
        AGGREGATION_READY,
        EMD_CODE
    }
}
