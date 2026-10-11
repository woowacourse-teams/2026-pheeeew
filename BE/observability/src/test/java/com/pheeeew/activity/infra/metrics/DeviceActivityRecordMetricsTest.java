package com.pheeeew.activity.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DeviceActivityRecordMetricsTest {

    @Test
    void 실패_카운터를_영으로_등록하고_고정된_사유별로_누적한다() {
        // given: 앱 타입 없이 모듈의 지표 빈을 스캔한다.
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new SimpleMeterRegistry();
            context.registerBean(MeterRegistry.class, () -> registry);
            context.scan("com.pheeeew.activity.infra.metrics");
            context.refresh();
            var metrics = context.getBean(DeviceActivityRecordMetrics.class);
            var saveFailures = registry.get("pheeeew.activity.record.failures")
                    .tag("reason", "save_failed").counter();
            var rejectedTasks = registry.get("pheeeew.activity.record.failures")
                    .tag("reason", "queue_rejected").counter();
            assertThat(saveFailures.count()).isZero();
            assertThat(rejectedTasks.count()).isZero();

            // when
            metrics.recordSaveFailure();
            metrics.recordQueueRejection();
            metrics.recordSaveFailure();

            // then
            assertThat(registry.getMeters()).hasSize(2);
            assertThat(saveFailures.count()).isEqualTo(2);
            assertThat(rejectedTasks.count()).isOne();
            assertThat(saveFailures.getId().getTags()).containsExactly(Tag.of("reason", "save_failed"));
            assertThat(rejectedTasks.getId().getTags()).containsExactly(Tag.of("reason", "queue_rejected"));
        }
    }
}
