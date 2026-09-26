package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class EmotionMetrics {

    private final MeterRegistry registry;
    private final Timer listQueryTimer;

    public EmotionMetrics(MeterRegistry registry) {
        this.registry = registry;
        listQueryTimer = Timer.builder("pheeeew.sigh.list.query")
                .description("List repository call duration, including failed calls")
                .register(registry);
        registerListResults(registry, "first", true);
        registerListResults(registry, "first", false);
        registerListResults(registry, "next", true);
        registerListResults(registry, "next", false);
    }

    public Timer.Sample startQuery() {
        return Timer.start(registry);
    }

    public void recordListQuery(Timer.Sample sample) {
        sample.stop(listQueryTimer);
    }

    public void recordListResult(String page, int returnedCount, boolean hasNext) {
        registry.get("pheeeew.sigh.list.results")
                .tags("page", page, "has_next", Boolean.toString(hasNext))
                .summary()
                .record(returnedCount);
    }

    private void registerListResults(MeterRegistry registry, String page, boolean hasNext) {
        DistributionSummary.builder("pheeeew.sigh.list.results")
                .description("Returned list item count per completed service call")
                .tag("page", page)
                .tag("has_next", Boolean.toString(hasNext))
                .register(registry);
    }
}
