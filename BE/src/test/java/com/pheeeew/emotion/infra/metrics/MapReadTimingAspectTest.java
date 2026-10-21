package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.emotion.domain.repository.EmotionMapReadRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.infra.metrics.MapReadMetrics.Stage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class MapReadTimingAspectTest {

    @Test
    void 실제_저장소_프록시의_차단_조회만_계측하고_결과를_보존한다() {
        // given
        var clock = new AtomicLong();
        var registry = new SimpleMeterRegistry();
        var target = mock(EmotionMapReadRepository.class);
        var blocks = EmotionMapBlocks.of(Set.of(10L), Set.of(20L));
        when(target.findBlockedTargets(1L)).thenAnswer(invocation -> {
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(12));
            return blocks;
        });
        var factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new MapReadTimingAspect(new MapReadMetrics(registry), clock::get));
        EmotionMapReadRepository proxy = factory.getProxy();

        // when
        var result = proxy.findBlockedTargets(1L);

        // then
        assertThat(result).isSameAs(blocks);
        var timer = registry.get("pheeeew.map.read.stage")
                .tags("stage", "map_blocks_lookup", "outcome", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(12);
        assertThat(registry.get("pheeeew.map.read.stage").timers().stream().mapToLong(t -> t.count()).sum()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 계측_장애가_조회_결과와_원래_예외를_덮어쓰지_않는다(boolean success) throws Throwable {
        // given
        var metrics = mock(MapReadMetrics.class);
        doThrow(new IllegalStateException("meter unavailable"))
                .when(metrics).record(Stage.MAP_BLOCKS_LOOKUP, 0, success);
        var aspect = new MapReadTimingAspect(metrics, () -> 0);
        var point = mock(ProceedingJoinPoint.class);
        var signature = mock(Signature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(EmotionMapReadRepository.class.getName());
        when(signature.getName()).thenReturn("findBlockedTargets");
        Object expected = new Object();
        var original = new IllegalArgumentException("original query error");
        when(point.proceed()).thenAnswer(invocation -> {
            if (!success) {
                throw original;
            }
            return expected;
        });

        // when / then
        if (success) {
            assertThat(aspect.record(point)).isSameAs(expected);
        } else {
            assertThatThrownBy(() -> aspect.record(point)).isSameAs(original);
        }
    }
}
