package com.pheeeew.device.infra.attestation.appattest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AppAttestMetrics {

    private final MeterRegistry registry;
    private final Counter accepted;

    public AppAttestMetrics(MeterRegistry registry) {
        this.registry = registry;
        accepted = Counter.builder("pheeeew.device.appattest.accepted")
                .description("Accepted App Attest attestation count")
                .register(registry);
    }

    public void recordAccepted() {
        accepted.increment();
    }

    public void recordRejected(AppAttestRejection rejection) {
        Counter.builder("pheeeew.device.appattest.rejected")
                .description("Rejected App Attest attestation count")
                .tag("reason", rejection.name())
                .register(registry)
                .increment();
    }
}
