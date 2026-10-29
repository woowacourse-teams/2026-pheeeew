package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.common.logging.RequestTimingStage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MapReadMetrics {

    private final Map<Stage, Timer> successes = new EnumMap<>(Stage.class);
    private final Map<Stage, Timer> errors = new EnumMap<>(Stage.class);

    public MapReadMetrics(MeterRegistry registry) {
        for (Stage stage : Stage.values()) {
            successes.put(stage, timer(registry, stage, "success"));
            errors.put(stage, timer(registry, stage, "error"));
        }
    }

    public void record(Stage stage, long durationNanos, boolean succeeded) {
        (succeeded ? successes : errors).get(stage).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Timer timer(MeterRegistry registry, Stage stage, String outcome) {
        return Timer.builder("pheeeew.map.read.stage")
                .description("Inclusive map read method duration; nested stages must not be summed")
                .tags("stage", stage.name().toLowerCase(Locale.ROOT), "outcome", outcome)
                .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(10),
                        Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
                        Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1))
                .register(registry);
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
        MAP_PINS_RESPONSE
    }
}
