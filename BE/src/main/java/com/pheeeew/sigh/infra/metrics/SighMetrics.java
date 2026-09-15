package com.pheeeew.sigh.infra.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SighMetrics {

    private final MeterRegistry registry;
    private final Timer mapQueryTimer;
    private final Timer listQueryTimer;
    private final DistributionSummary completeMapResults;
    private final DistributionSummary truncatedMapResults;

    public SighMetrics(MeterRegistry registry) {
        this.registry = registry;
        mapQueryTimer = Timer.builder("pheeeew.sigh.map.query")
                .description("Map repository call duration, including failed calls")
                .register(registry);
        listQueryTimer = Timer.builder("pheeeew.sigh.list.query")
                .description("List repository call duration, including failed calls")
                .register(registry);
        completeMapResults = registerMapResults(registry, false);
        truncatedMapResults = registerMapResults(registry, true);
        registerListResults(registry, "first", true);
        registerListResults(registry, "first", false);
        registerListResults(registry, "next", true);
        registerListResults(registry, "next", false);
    }

    public Timer.Sample startQuery() {
        return Timer.start(registry);
    }

    public void recordMapQuery(Timer.Sample sample) {
        sample.stop(mapQueryTimer);
    }

    public void recordListQuery(Timer.Sample sample) {
        sample.stop(listQueryTimer);
    }

    public void recordMapResult(int returnedCount, boolean truncated) {
        DistributionSummary results = truncated ? truncatedMapResults : completeMapResults;
        results.record(returnedCount);
    }

    public void recordListResult(String page, int returnedCount, boolean hasNext) {
        registry.get("pheeeew.sigh.list.results")
                .tags("page", page, "has_next", Boolean.toString(hasNext))
                .summary()
                .record(returnedCount);
    }

    private DistributionSummary registerMapResults(MeterRegistry registry, boolean truncated) {
        return DistributionSummary.builder("pheeeew.sigh.map.results")
                .description("Returned map item count per completed service call")
                .tag("truncated", Boolean.toString(truncated))
                .register(registry);
    }

    private void registerListResults(MeterRegistry registry, String page, boolean hasNext) {
        DistributionSummary.builder("pheeeew.sigh.list.results")
                .description("Returned list item count per completed service call")
                .tag("page", page)
                .tag("has_next", Boolean.toString(hasNext))
                .register(registry);
    }
}
