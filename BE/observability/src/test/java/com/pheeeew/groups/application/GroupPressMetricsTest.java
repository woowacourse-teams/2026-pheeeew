package com.pheeeew.groups.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class GroupPressMetricsTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private GroupPressMetrics metrics;

    @BeforeEach
    void setUp() {
        context.registerBean(MeterRegistry.class, () -> registry);
        context.scan("com.pheeeew.groups.application");
        context.refresh();
        metrics = context.getBean(GroupPressMetrics.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void 적용량이_없으면_빈_요청만_누적한다() {
        // given / when
        metrics.recordApplied(0, 0, 0, 0);
        metrics.recordApplied(0, 0, 0, 0);

        // then
        var empty = registry.get("pheeeew.group.press.empty").counter();
        assertThat(empty.count()).isEqualTo(2);
        assertThat(empty.getId().getTags()).isEmpty();
        assertSummary("pheeeew.group.press.applied", 0, 0);
        assertSummary("pheeeew.group.press.states", 0, 0);
        assertSummary("pheeeew.group.press.clamped", 0, 0, Tag.of("limit", "per_state"));
        assertSummary("pheeeew.group.press.clamped", 0, 0, Tag.of("limit", "total"));
    }

    @Test
    void 적용량과_상태수를_누적하고_제한_초과량은_발생한_요청만_기록한다() {
        // given / when: 정상, 감정별 제한, 전체 제한, 두 제한 동시 적용
        metrics.recordApplied(3, 2, 0, 0);
        metrics.recordApplied(30, 1, 15, 0);
        metrics.recordApplied(100, 4, 0, 50);
        metrics.recordApplied(100, 4, 20, 50);

        // then
        assertSummary("pheeeew.group.press.applied", 4, 233);
        assertSummary("pheeeew.group.press.states", 4, 11);
        assertSummary("pheeeew.group.press.clamped", 2, 35, Tag.of("limit", "per_state"));
        assertSummary("pheeeew.group.press.clamped", 2, 100, Tag.of("limit", "total"));
        assertThat(registry.get("pheeeew.group.press.empty").counter().count()).isZero();
    }

    @Test
    void 요청_형식과_교착_오류를_기존_라벨로_따로_누적한다() {
        // given / when
        metrics.recordFormat(true);
        metrics.recordFormat(true);
        metrics.recordFormat(false);
        metrics.recordDeadlock();
        metrics.recordDeadlock();

        // then
        assertCounter("pheeeew.group.press.requests", 2, Tag.of("format", "counts"));
        assertCounter("pheeeew.group.press.requests", 1, Tag.of("format", "state"));
        assertCounter("pheeeew.group.press.deadlocks", 2);
        assertCounter("pheeeew.group.press.empty", 0);
        assertThat(registry.getMeters()).hasSize(8);
    }

    private void assertCounter(String name, double count, Tag... tags) {
        var counter = registry.get(name).tags(List.of(tags)).counter();
        assertThat(counter.count()).isEqualTo(count);
        assertThat(counter.getId().getTags()).containsExactlyInAnyOrder(tags);
    }

    private void assertSummary(String name, long count, double total, Tag... tags) {
        var summary = registry.get(name).tags(List.of(tags)).summary();
        assertThat(summary.count()).isEqualTo(count);
        assertThat(summary.totalAmount()).isEqualTo(total);
        assertThat(summary.getId().getTags()).containsExactlyInAnyOrder(tags);
    }
}
