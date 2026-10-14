package com.pheeeew.activity.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeviceActivityRecordMetrics {

    private final Counter saveFailures;
    private final Counter rejectedTasks;

    public DeviceActivityRecordMetrics(MeterRegistry registry) {
        saveFailures = registry.counter("pheeeew.activity.record.failures", "reason", "save_failed");
        rejectedTasks = registry.counter("pheeeew.activity.record.failures", "reason", "queue_rejected");
    }

    public void recordSaveFailure() {
        saveFailures.increment();
    }

    public void recordQueueRejection() {
        rejectedTasks.increment();
    }
}
