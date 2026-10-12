package com.pheeeew.activity.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DeviceActivityAggregationMetricsTest {

    @ParameterizedTest
    @ValueSource(strings = {"prod", "dev", ""})
    void 집계_실패는_prod에서만_라벨_없이_등록하고_기록기_카운터와_독립적으로_누적한다(String profile) {
        // given
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new SimpleMeterRegistry();
            if (!profile.isEmpty()) {
                context.getEnvironment().setActiveProfiles(profile);
            }
            context.registerBean(MeterRegistry.class, () -> registry);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.scan("com.pheeeew.activity.infra.metrics");

            // when
            context.refresh();
            context.getBean(DeviceActivityRecordMetrics.class).recordSaveFailure();

            // then
            assertThat(registry.get("pheeeew.activity.record.failures")
                    .tag("reason", "save_failed").counter().count()).isOne();
            assertThat(registry.get("pheeeew.activity.record.failures")
                    .tag("reason", "queue_rejected").counter().count()).isZero();
            if (profile.equals("prod")) {
                var counter = registry.get("pheeeew.activity.aggregation.failures").counter();
                assertThat(counter.count()).isZero();
                assertThat(counter.getId().getTags()).isEmpty();
                var metrics = context.getBean(DeviceActivityAggregationMetrics.class);
                metrics.recordFailure();
                metrics.recordFailure();
                assertThat(counter.count()).isEqualTo(2);
                assertThat(registry.getMeters()).hasSize(9);
                assertThat(context.getBeansOfType(DeviceActivityMetrics.class)).hasSize(1);
                for (String platform : new String[]{"android", "ios"}) {
                    assertThat(registry.get("pheeeew.activity.dau").tag("platform", platform).gauge().value()).isNaN();
                    assertThat(registry.get("pheeeew.activity.mau").tag("platform", platform).gauge().value()).isNaN();
                }
                assertThat(registry.get("pheeeew.activity.last.aggregated").gauge().value()).isNaN();
                assertThat(registry.get("pheeeew.activity.collection.started").gauge().value()).isNaN();
            } else {
                assertThat(registry.find("pheeeew.activity.aggregation.failures").counter()).isNull();
                assertThat(registry.getMeters()).hasSize(2);
                assertThat(context.getBeansOfType(DeviceActivityMetrics.class)).isEmpty();
            }
        }
    }
}
