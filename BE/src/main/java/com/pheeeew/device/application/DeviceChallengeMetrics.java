package com.pheeeew.device.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeviceChallengeMetrics {

    private final Counter issued;
    private final Counter deleted;

    public DeviceChallengeMetrics(MeterRegistry registry) {
        issued = Counter.builder("pheeeew.device.challenge.issued")
                .description("Issued device attestation challenge count")
                .register(registry);
        deleted = Counter.builder("pheeeew.device.challenge.deleted")
                .description("Deleted expired device attestation challenge count")
                .register(registry);
    }

    public void recordIssued() {
        issued.increment();
    }

    public void recordDeleted(int deletedCount) {
        deleted.increment(deletedCount);
    }
}
