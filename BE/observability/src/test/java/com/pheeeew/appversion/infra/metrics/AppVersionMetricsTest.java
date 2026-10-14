package com.pheeeew.appversion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AppVersionMetricsTest {

    @Test
    void 플랫폼별_카운터를_영으로_등록하고_기존_라벨로_독립적으로_누적한다() {
        // given: HTTP 필터와 앱 도메인 없이 모듈만 스캔한다.
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new SimpleMeterRegistry();
            context.registerBean(MeterRegistry.class, () -> registry);
            context.scan("com.pheeeew.appversion.infra.metrics");
            context.refresh();
            var metrics = context.getBean(AppVersionMetrics.class);
            var android = registry.get("pheeeew.app.version.checks").tag("platform", "android").counter();
            var ios = registry.get("pheeeew.app.version.checks").tag("platform", "ios").counter();
            assertThat(android.count()).isZero();
            assertThat(ios.count()).isZero();

            // when
            metrics.recordAndroidCheck();
            metrics.recordIosCheck();
            metrics.recordAndroidCheck();

            // then
            assertThat(registry.getMeters()).hasSize(2);
            assertThat(android.count()).isEqualTo(2);
            assertThat(ios.count()).isOne();
            assertThat(android.getId().getTags()).containsExactly(Tag.of("platform", "android"));
            assertThat(ios.getId().getTags()).containsExactly(Tag.of("platform", "ios"));
        }
    }
}
