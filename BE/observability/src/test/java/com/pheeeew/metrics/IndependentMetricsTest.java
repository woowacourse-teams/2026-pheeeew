package com.pheeeew.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.application.DeviceChallengeMetrics;
import com.pheeeew.emotion.infra.metrics.EmotionMetrics;
import com.pheeeew.emotion.infra.metrics.MapReadMetrics;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics.Operation;
import com.pheeeew.report.application.EmotionReportMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class IndependentMetricsTest {

    @Test
    void 앱_타입_없이_스캔한_지표가_기존_이름과_라벨로_값을_기록한다() {
        // given: 앱 클래스가 없는 모듈 classpath에서 기존 패키지를 스캔한다.
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new SimpleMeterRegistry();
            context.registerBean(MeterRegistry.class, () -> registry);
            context.scan("com.pheeeew.emotion.infra.metrics", "com.pheeeew.region.infra.metrics",
                    "com.pheeeew.device.application", "com.pheeeew.report.application");
            context.refresh();

            // when
            EmotionMetrics emotions = context.getBean(EmotionMetrics.class);
            Timer.Sample sample = emotions.startQuery();
            emotions.recordListQuery(sample);
            emotions.recordListResult("next", 3, false);
            emotions.recordCreateThrottled();
            DeviceChallengeMetrics challenges = context.getBean(DeviceChallengeMetrics.class);
            challenges.recordIssued();
            challenges.recordIssued();
            challenges.recordDeleted(5);
            challenges.recordDeleted(2);
            context.getBean(EmotionReportMetrics.class).recordAutoDeleted(4);
            context.getBean(RegionQueryMetrics.class).recordQuery(Operation.EMD_CODE, 25_000_000, false);
            context.getBean(MapReadMetrics.class).record(MapReadMetrics.Stage.MAP_BLOCKS_LOOKUP, 7_000_000, true);

            // then
            // SimpleMeterRegistry가 파생 등록하는 버킷 Gauge와 직접 정의한 지표를 구분한다.
            var definedMeters = registry.getMeters().stream()
                    .filter(meter -> meter instanceof Timer || meter instanceof Counter
                            || meter instanceof DistributionSummary)
                    .toList();
            assertThat(definedMeters).hasSize(18);
            assertThat(registry.get("pheeeew.map.read.stage").timers()).singleElement().satisfies(timer -> {
                assertThat(timer.count()).isEqualTo(1);
                assertThat(timer.getId().getTag("api_version")).isEqualTo("unscoped");
            });
            assertThat(definedMeters).extracting(meter -> meter.getId().getName())
                    .containsOnly("pheeeew.sigh.list.query", "pheeeew.sigh.list.results",
                            "pheeeew.emotion.create.throttled", "pheeeew.region.query",
                            "pheeeew.device.challenge.issued", "pheeeew.device.challenge.deleted",
                            "pheeeew.report.sigh.auto_deleted", "pheeeew.map.read.stage");
            assertCounter(registry, "pheeeew.emotion.create.throttled", 1);
            assertCounter(registry, "pheeeew.device.challenge.issued", 2);
            assertCounter(registry, "pheeeew.device.challenge.deleted", 7);
            assertCounter(registry, "pheeeew.report.sigh.auto_deleted", 4);
            Timer listQuery = registry.get("pheeeew.sigh.list.query").timer();
            assertThat(listQuery.count()).isOne();
            assertThat(listQuery.getId().getTags()).isEmpty();
            var results = registry.get("pheeeew.sigh.list.results")
                    .tags("page", "next", "has_next", "false").summary();
            assertThat(results.count()).isOne();
            assertThat(results.totalAmount()).isEqualTo(3);
            assertThat(results.getId().getTags())
                    .containsExactlyInAnyOrder(Tag.of("page", "next"), Tag.of("has_next", "false"));
            Timer regionQuery = registry.get("pheeeew.region.query")
                    .tags("operation", "emd_code", "outcome", "error").timer();
            assertThat(regionQuery.count()).isOne();
            assertThat(regionQuery.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25);
            assertThat(regionQuery.getId().getTags())
                    .containsExactlyInAnyOrder(Tag.of("operation", "emd_code"), Tag.of("outcome", "error"));
        }
    }

    private void assertCounter(MeterRegistry registry, String name, double count) {
        Counter counter = registry.get(name).counter();
        assertThat(counter.count()).isEqualTo(count);
        assertThat(counter.getId().getTags()).isEmpty();
    }
}
