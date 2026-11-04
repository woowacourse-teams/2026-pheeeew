package com.pheeeew.device.infra.attestation.appattest;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AppAttestMetricsTest {

    @Test
    void 승인만_영으로_등록하고_거절은_발생한_사유별로_생성해_누적한다() {
        // given
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new AppAttestMetrics(registry);
            var accepted = registry.get("pheeeew.device.appattest.accepted").counter();
            assertThat(accepted.count()).isZero();
            assertThat(accepted.getId().getTags()).isEmpty();
            assertThat(registry.find("pheeeew.device.appattest.rejected").counters()).isEmpty();
            assertThat(AppAttestRejection.values()).extracting(Enum::name).containsExactly(
                    "ATTESTATION_REQUIRED", "PROPERTIES_MISSING", "MALFORMED_TOKEN", "KEY_ID_MISSING",
                    "MALFORMED_ATTESTATION", "UNSUPPORTED_FORMAT", "MALFORMED_AUTH_DATA", "CHALLENGE_INVALID",
                    "CERTIFICATE_CHAIN_INVALID", "NONCE_MISMATCH", "KEY_ID_MISMATCH", "APP_ID_MISMATCH",
                    "COUNTER_NOT_ZERO", "ENVIRONMENT_MISMATCH", "CREDENTIAL_ID_MISMATCH");

            // when
            metrics.recordAccepted();
            metrics.recordAccepted();
            for (AppAttestRejection reason : AppAttestRejection.values()) {
                metrics.recordRejected(reason);
                metrics.recordRejected(reason);
            }

            // then
            assertThat(accepted.count()).isEqualTo(2);
            for (AppAttestRejection reason : AppAttestRejection.values()) {
                var rejected = registry.get("pheeeew.device.appattest.rejected")
                        .tag("reason", reason.name()).counter();
                assertThat(rejected.count()).isEqualTo(2);
                assertThat(rejected.getId().getTags()).containsExactly(Tag.of("reason", reason.name()));
            }
        } finally {
            registry.close();
        }
    }
}
