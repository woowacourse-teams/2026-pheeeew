package com.pheeeew.device.infra.attestation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PlayIntegrityMetrics {

    private static final String UNKNOWN_APP_RECOGNITION_VERDICT = "UNKNOWN";
    private static final Set<String> KNOWN_APP_RECOGNITION_VERDICTS =
            Set.of("PLAY_RECOGNIZED", "PLAY_UNRECOGNIZED", "UNRECOGNIZED_VERSION", "UNEVALUATED");

    private final MeterRegistry registry;
    private final Counter accepted;
    private final Counter skipped;

    public PlayIntegrityMetrics(MeterRegistry registry) {
        this.registry = registry;
        accepted = Counter.builder("pheeeew.device.attestation.accepted")
                .description("Accepted device attestation count")
                .register(registry);
        skipped = Counter.builder("pheeeew.device.attestation.skipped")
                .description("Skipped device attestation count")
                .register(registry);
    }

    public void recordVerdict(String appRecognitionVerdict, boolean meetsDeviceIntegrity) {
        Counter.builder("pheeeew.device.attestation.verdict")
                .description("Decoded device attestation verdict count")
                .tag("appRecognition", boundedAppRecognitionVerdict(appRecognitionVerdict))
                .tag("deviceIntegrity", Boolean.toString(meetsDeviceIntegrity))
                .register(registry)
                .increment();
    }

    public void recordRejected(PlayIntegrityRejection rejection) {
        Counter.builder("pheeeew.device.attestation.rejected")
                .description("Rejected device attestation count")
                .tag("reason", rejection.name())
                .register(registry)
                .increment();
    }

    public void recordAccepted() {
        accepted.increment();
    }

    public void recordSkipped() {
        skipped.increment();
    }

    private String boundedAppRecognitionVerdict(String appRecognitionVerdict) {
        if (KNOWN_APP_RECOGNITION_VERDICTS.contains(appRecognitionVerdict)) {
            return appRecognitionVerdict;
        }
        return UNKNOWN_APP_RECOGNITION_VERDICT;
    }
}
