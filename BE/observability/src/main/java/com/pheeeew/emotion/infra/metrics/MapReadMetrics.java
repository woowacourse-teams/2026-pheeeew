package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.common.logging.RequestTimingStage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MapReadMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<TimerKey, Timer> timers = new ConcurrentHashMap<>();

    public MapReadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Stage stage, long durationNanos, boolean succeeded) {
        TimerKey key = new TimerKey(stage, MapReadObservation.apiVersion(), succeeded ? "success" : "error");
        timers.computeIfAbsent(key, this::timer).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Timer timer(TimerKey key) {
        return Timer.builder("pheeeew.map.read.stage")
                .description("Inclusive map read method duration; nested stages must not be summed")
                .tags("stage", key.stage().name().toLowerCase(Locale.ROOT), "outcome", key.outcome(),
                        "api_version", key.apiVersion())
                .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(10),
                        Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
                        Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1))
                .register(registry);
    }

    private record TimerKey(Stage stage, String apiVersion, String outcome) {
    }

    public enum Stage implements RequestTimingStage {
        MAP_PINS_CONTROLLER,
        MAP_REGIONS_CONTROLLER,
        MAP_PINS_SERVICE,
        MAP_REGIONS_SERVICE,
        MAP_PINS_DEVICE,
        MAP_REGIONS_PREPARE,
        MAP_PINS_CACHE,
        MAP_REGIONS_CACHE,
        MAP_BLOCKS_LOOKUP,
        MAP_REGION_LIST,
        MAP_PINS_RESPONSE,
        MAP_PINS_DB_PATH,
        MAP_REGIONS_DB_PATH,
        MAP_JWT_DECODE,
        MAP_FILTER_TOTAL,
        MAP_PRE_MVC,
        MAP_MVC_PRE_CONTROLLER,
        MAP_RESPONSE_PROCESSING,
        MAP_FILTER_RETURN,
        MAP_JSON_WRITE
    }
}
