package com.pheeeew.appversion.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AppVersionMetrics {

    private final Counter androidChecks;
    private final Counter iosChecks;

    public AppVersionMetrics(MeterRegistry registry) {
        androidChecks = register(registry, "android");
        iosChecks = register(registry, "ios");
    }

    public void recordAndroidCheck() {
        androidChecks.increment();
    }

    public void recordIosCheck() {
        iosChecks.increment();
    }

    private Counter register(MeterRegistry registry, String platform) {
        return Counter.builder("pheeeew.app.version.checks")
                .description("Successful app version policy HTTP request count")
                .tag("platform", platform)
                .register(registry);
    }
}
