package com.pheeeew.sigh.infra.metrics;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.sigh.application.EmotionListCursorCodec;
import com.pheeeew.sigh.application.EmotionLocationGenerator;
import com.pheeeew.sigh.application.EmotionNicknameGenerator;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.dto.EmotionListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.domain.repository.projection.EmotionListProjection;
import com.pheeeew.sigh.domain.repository.projection.EmotionMapProjection;
import com.pheeeew.sigh.domain.repository.query.EmotionQueryPeriod;
import com.pheeeew.sigh.domain.repository.query.SighSearchBounds;
import com.pheeeew.sigh.exception.SighException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class EmotionMetricsAspectTest {

    private static final SighSearchBounds BOUNDS = SighSearchBounds.of(126.9, 37.5, 127.1, 37.6);

    private static final Instant SNAPSHOT_AT = Instant.parse("2026-09-15T00:00:00Z");
    private static final EmotionQueryPeriod PERIOD = EmotionQueryPeriod.of(
            Instant.parse("2026-09-01T15:00:00Z"), SNAPSHOT_AT
    );

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final EmotionRepository repository = mock(EmotionRepository.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private SighService service;

    @BeforeEach
    void setUp() {
        context.registerBean(EmotionRepository.class, () -> repository);
        context.registerBean(DeviceRepository.class, () -> mock(DeviceRepository.class));
        context.registerBean(EmotionLocationGenerator.class, () -> mock(EmotionLocationGenerator.class));
        context.registerBean(EmotionNicknameGenerator.class, () -> mock(EmotionNicknameGenerator.class));
        context.registerBean(SimpleMeterRegistry.class, () -> registry);
        context.registerBean(Clock.class, () -> Clock.fixed(SNAPSHOT_AT, ZoneId.of("Asia/Seoul")));
        context.register(
                AopAutoConfiguration.class,
                EmotionMetrics.class, EmotionMetricsAspect.class, SighService.class
        );
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
        EmotionMapProjection projection = mock(EmotionMapProjection.class);
        when(projection.getId()).thenAnswer(invocation -> {
            clock.add(Duration.ofMillis(50));
            return 1L;
        });
        when(projection.getLongitude()).thenReturn(127.0);
        when(projection.getLatitude()).thenReturn(37.55);
        when(projection.getCreatedAt()).thenReturn(Instant.parse("2026-09-11T00:00:00Z"));
        when(repository.findAllWithinBounds(
                eq(BOUNDS), any(EmotionQueryPeriod.class), isNull(), eq(501)
        ))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(250));
                    return Collections.nCopies(fetched, projection);
                });

        // when
        SighMapResult result = service.findAllWithinBounds(BOUNDS, Optional.empty());

        // then
        assertThat(result.sighs()).hasSize(returned);
        assertThat(result.truncated()).isEqualTo(truncated);
        verify(repository).findAllWithinBounds(
                eq(BOUNDS), any(EmotionQueryPeriod.class), isNull(), eq(501)
        );
        Timer query = registry.get("pheeeew.sigh.map.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        DistributionSummary results = registry.get("pheeeew.sigh.map.results")
                .tag("truncated", Boolean.toString(truncated)).summary();
        assertThat(results.count()).isEqualTo(1);
        assertThat(results.totalAmount()).isEqualTo(returned);
        assertThat(registry.get("pheeeew.sigh.map.results")
                .tag("truncated", Boolean.toString(!truncated)).summary().count()).isZero();
        assertThat(registry.getMeters()).hasSize(8);
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isZero();
        assertThat(query.getId().getTags()).isEmpty();
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.getId().getTags()).hasSize(1));
    }

    @Test
    void 조회가_실패해도_시간은_기록하고_결과_개수는_기록하지_않으며_같은_예외를_전파한다() {
        // given
        IllegalStateException failure = new IllegalStateException("query failed");
        when(repository.findAllWithinBounds(
                eq(BOUNDS), any(EmotionQueryPeriod.class), isNull(), eq(501)
        ))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(100));
                    throw failure;
                });

        // when / then
        assertThatThrownBy(() -> service.findAllWithinBounds(BOUNDS, Optional.empty())).isSameAs(failure);
        Timer query = registry.get("pheeeew.sigh.map.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }

    @Test
    void 다른_조회는_지도와_목록_전용_지표에_기록하지_않는다() {
        // given
        Device device = 기본_기기_빌더().build();
        ReflectionTestUtils.setField(device, "id", 1L);
        when(context.getBean(DeviceRepository.class).findByPublicId(device.getPublicId()))
                .thenReturn(Optional.of(device));

        // when / then
        context.getBean(EmotionRepository.class).count();
        assertThatThrownBy(() -> service.findById(1L, device.getPublicId()))
                .isInstanceOf(SighException.class);
        verify(repository).findById(1L, device.getId());
        assertThat(registry.get("pheeeew.sigh.map.query").timer().count()).isZero();
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isZero();
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void 목록_조회_시간만_기록하고_반환값을_그대로_전달한다(int fetched) {
        // given
        List<EmotionListProjection> projections = Collections.nCopies(fetched, mock(EmotionListProjection.class));
        when(repository.findListWithinBounds(BOUNDS, PERIOD, SNAPSHOT_AT, Long.MAX_VALUE, 1L, 500, 21, 1L))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(250));
                    return projections;
                });

        // when
        List<EmotionListProjection> result = context.getBean(EmotionRepository.class).findListWithinBounds(
                BOUNDS, PERIOD, SNAPSHOT_AT, Long.MAX_VALUE, 1L, 500, 21, 1L
        );
        clock.add(Duration.ofMillis(50));

        // then
        assertThat(result).isSameAs(projections);
        verify(repository).findListWithinBounds(BOUNDS, PERIOD, SNAPSHOT_AT, Long.MAX_VALUE, 1L, 500, 21, 1L);
        Timer query = registry.get("pheeeew.sigh.list.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        assertThat(query.getId().getTags()).isEmpty();
        assertThat(registry.getMeters()).hasSize(8);
        assertThat(registry.get("pheeeew.sigh.map.query").timer().count()).isZero();
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }

    @Test
    void 목록_조회가_실패해도_시간을_기록하고_같은_예외를_전파한다() {
        // given
        IllegalStateException failure = new IllegalStateException("query failed");
        when(repository.findListWithinBounds(BOUNDS, PERIOD, SNAPSHOT_AT, Long.MAX_VALUE, 1L, 500, 21, 1L))
                .thenAnswer(invocation -> {
                    clock.add(Duration.ofMillis(100));
                    throw failure;
                });

        // when / then
        assertThatThrownBy(() -> context.getBean(EmotionRepository.class).findListWithinBounds(
                BOUNDS, PERIOD, SNAPSHOT_AT, Long.MAX_VALUE, 1L, 500, 21, 1L
        )).isSameAs(failure);
        Timer query = registry.get("pheeeew.sigh.list.query").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"first, 0, 0, false", "first, 20, 20, false", "first, 21, 20, true",
            "next, 0, 0, false", "next, 20, 20, false", "next, 21, 20, true"})
    void 첫_페이지와_다음_페이지의_실제_반환_개수와_다음_페이지_여부를_기록한다(
            String page, int fetched, int returned, boolean hasNext
    ) {
        // given
        UUID devicePublicId = 등록된_기기_식별자();
        EmotionListProjection projection = mock(EmotionListProjection.class);
        when(projection.getId()).thenReturn(1L);
        when(projection.getLongitude()).thenReturn(127.0);
        when(projection.getLatitude()).thenReturn(37.55);
        when(projection.getCreatedAt()).thenReturn(SNAPSHOT_AT.minusSeconds(1));
        when(repository.findListWithinBounds(
                eq(BOUNDS), any(EmotionQueryPeriod.class), any(Instant.class), anyLong(),
                eq(1L), eq(500), eq(21), eq(1L)
        )).thenReturn(Collections.nCopies(fetched, projection));

        // when
        SighListResult result = 목록을_조회한다(page, devicePublicId);

        // then
        assertThat(result.items()).hasSize(returned);
        assertThat(result.hasNext()).isEqualTo(hasNext);
        목록_결과_집계를_검증한다(page, returned, hasNext);
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isEqualTo(1);
        assertThat(registry.get("pheeeew.sigh.map.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = {"first", "next"})
    void 목록_조회_실패는_성공_호출과_반환_개수에_포함하지_않는다(String page) {
        // given
        UUID devicePublicId = 등록된_기기_식별자();
        IllegalStateException failure = new IllegalStateException("query failed");
        when(repository.findListWithinBounds(
                eq(BOUNDS), any(EmotionQueryPeriod.class), any(Instant.class), anyLong(),
                eq(1L), eq(500), eq(21), eq(1L)
        )).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> 목록을_조회한다(page, devicePublicId)).isSameAs(failure);
        assertThat(registry.get("pheeeew.sigh.list.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isEqualTo(1);
    }

    @Test
    void 잘못된_커서는_목록_성공_호출에_포함하지_않는다() {
        // given / when / then
        assertThatThrownBy(() -> service.findNextListPage("invalid", UUID.randomUUID()))
                .isInstanceOf(SighException.class);
        assertThat(registry.get("pheeeew.sigh.list.results").summaries())
                .allSatisfy(summary -> assertThat(summary.count()).isZero());
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isZero();
    }

    @Test
    void 조회_기간이_지난_커서의_빈_목록도_정상_반환이면_한_번_기록한다() {
        // given
        UUID devicePublicId = 등록된_기기_식별자();
        EmotionListCursor cursor = EmotionListCursor.initial(BOUNDS, SNAPSHOT_AT.minus(Duration.ofDays(30)));

        // when
        SighListResult result = service.findNextListPage(EmotionListCursorCodec.encode(cursor), devicePublicId);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        목록_결과_집계를_검증한다("next", 0, false);
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isZero();
    }

    private UUID 등록된_기기_식별자() {
        Device device = 기본_기기_빌더().build();
        ReflectionTestUtils.setField(device, "id", 1L);
        when(context.getBean(DeviceRepository.class).findByPublicId(device.getPublicId()))
                .thenReturn(Optional.of(device));
        return device.getPublicId();
    }

    private SighListResult 목록을_조회한다(String page, UUID devicePublicId) {
        if ("first".equals(page)) {
            return service.findFirstListPage(BOUNDS, devicePublicId);
        }
        EmotionListCursor cursor = EmotionListCursor.of(BOUNDS, SNAPSHOT_AT, SNAPSHOT_AT.minusSeconds(1), 42L);
        return service.findNextListPage(EmotionListCursorCodec.encode(cursor), devicePublicId);
    }

    private void 목록_결과_집계를_검증한다(String page, int returned, boolean hasNext) {
        DistributionSummary recorded = registry.get("pheeeew.sigh.list.results")
                .tags("page", page, "has_next", Boolean.toString(hasNext)).summary();
        assertThat(recorded.count()).isEqualTo(1);
        assertThat(recorded.totalAmount()).isEqualTo(returned);
        assertThat(registry.get("pheeeew.sigh.list.results").summaries())
                .hasSize(4)
                .allSatisfy(summary -> {
                    assertThat(summary.getId().getTags()).extracting(tag -> tag.getKey())
                            .containsExactlyInAnyOrder("page", "has_next");
                    if (summary != recorded) {
                        assertThat(summary.count()).isZero();
                    }
                });
    }
}
