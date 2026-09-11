package com.pheeeew.sigh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.projection.SighMapProjection;
import com.pheeeew.sigh.exception.SighException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SighMapMetricsAspectTest {

    private static final SighSearchBounds BOUNDS = SighSearchBounds.of(126.9, 37.5, 127.1, 37.6);

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final SighRepository repository = mock(SighRepository.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private SighService service;

    @BeforeEach
    void setUp() {
        context.registerBean(SighRepository.class, () -> repository);
        context.registerBean(SighLocationGenerator.class, () -> mock(SighLocationGenerator.class));
        context.registerBean(SighNicknameGenerator.class, () -> mock(SighNicknameGenerator.class));
        context.registerBean(SimpleMeterRegistry.class, () -> registry);
        context.register(AopAutoConfiguration.class, SighMapMetrics.class, SighMapMetricsAspect.class, SighService.class);
        context.refresh();
        service = context.getBean(SighService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @ParameterizedTest
    @CsvSource({"0, 0, false", "500, 500, false", "501, 500, true"})
    void 조회_시간과_실제_반환_개수_및_잘림을_기록한다(int fetched, int returned, boolean truncated) {
        // given
        SighMapProjection projection = mock(SighMapProjection.class);
        when(projection.getId()).thenAnswer(invocation -> {
            clock.add(Duration.ofMillis(50));
            return 1L;
        });
        when(projection.getLongitude()).thenReturn(127.0);
        when(projection.getLatitude()).thenReturn(37.55);
        when(projection.getCreatedAt()).thenReturn(Instant.parse("2026-09-11T00:00:00Z"));
        when(repository.findAllWithinBounds(126.9, 37.5, 127.1, 37.6, 501))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(250));
                    return Collections.nCopies(fetched, projection);
                });

        // when
        SighMapResult result = service.findAllWithinBounds(BOUNDS);

        // then
        assertThat(result.sighs()).hasSize(returned);
        assertThat(result.truncated()).isEqualTo(truncated);
        verify(repository).findAllWithinBounds(126.9, 37.5, 127.1, 37.6, 501);
        Timer query = registry.get("pheeeew.sigh.map.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        DistributionSummary results = registry.get("pheeeew.sigh.map.results")
                .tag("truncated", Boolean.toString(truncated)).summary();
        assertThat(results.count()).isEqualTo(1);
        assertThat(results.totalAmount()).isEqualTo(returned);
        assertThat(registry.get("pheeeew.sigh.map.results")
                .tag("truncated", Boolean.toString(!truncated)).summary().count()).isZero();
        assertThat(registry.getMeters()).hasSize(3);
        assertThat(query.getId().getTags()).isEmpty();
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.getId().getTags()).hasSize(1));
    }

    @Test
    void 조회가_실패해도_시간은_기록하고_결과_개수는_기록하지_않으며_같은_예외를_전파한다() {
        // given
        IllegalStateException failure = new IllegalStateException("query failed");
        when(repository.findAllWithinBounds(126.9, 37.5, 127.1, 37.6, 501))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(100));
                    throw failure;
                });

        // when / then
        assertThatThrownBy(() -> service.findAllWithinBounds(BOUNDS)).isSameAs(failure);
        Timer query = registry.get("pheeeew.sigh.map.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }

    @Test
    void 다른_조회는_지도_전용_지표에_기록하지_않는다() {
        // when / then
        context.getBean(SighRepository.class).count();
        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(SighException.class);
        assertThat(registry.get("pheeeew.sigh.map.query").timer().count()).isZero();
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }
}
