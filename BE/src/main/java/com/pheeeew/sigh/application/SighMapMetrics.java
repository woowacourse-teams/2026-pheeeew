package com.pheeeew.sigh.application;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SighMapMetrics {

    private final MeterRegistry registry;
    private final Timer queryTimer;
    private final DistributionSummary completeResults;
    private final DistributionSummary truncatedResults;

    public SighMapMetrics(MeterRegistry registry) {
        this.registry = registry;
        queryTimer = Timer.builder("pheeeew.sigh.map.query")
                .description("Map repository call duration, including failed calls")
                .register(registry);
        completeResults = registerResults(registry, false);
        truncatedResults = registerResults(registry, true);
    }

    public Timer.Sample startQuery() {
        return Timer.start(registry);
    }

    public void recordQuery(Timer.Sample sample) {
        sample.stop(queryTimer);
    }

    public void recordResult(int returnedCount, boolean truncated) {
        DistributionSummary results = truncated ? truncatedResults : completeResults;
        results.record(returnedCount);
    }

    private DistributionSummary registerResults(MeterRegistry registry, boolean truncated) {
        return DistributionSummary.builder("pheeeew.sigh.map.results")
                .description("Returned map item count per completed service call")
                .tag("truncated", Boolean.toString(truncated))
                .register(registry);
    }
}
