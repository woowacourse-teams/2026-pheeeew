package com.pheeeew.activity.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
public class DeviceActivityAggregationMetrics {

    private final Counter failures;

    public DeviceActivityAggregationMetrics(MeterRegistry registry) {
        failures = registry.counter("pheeeew.activity.aggregation.failures");
    }

    public void recordFailure() {
        failures.increment();
    }
}
