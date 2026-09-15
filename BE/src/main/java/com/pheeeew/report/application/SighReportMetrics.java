package com.pheeeew.report.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SighReportMetrics {

    private final Counter autoDeleted;

    public SighReportMetrics(MeterRegistry registry) {
        autoDeleted = Counter.builder("pheeeew.report.sigh.auto_deleted")
                .description("Automatically deleted sigh count by report threshold")
                .register(registry);
    }

    public void recordAutoDeleted(int deletedCount) {
        autoDeleted.increment(deletedCount);
    }
}
