package com.pheeeew.activity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.activity.application.DeviceActivityAggregationService;
import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetrics;
import com.pheeeew.device.domain.DevicePlatform;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DeviceActivityAggregationSchedulerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-17T14:59:00Z");

    private final DeviceActivityAggregationService service = mock(DeviceActivityAggregationService.class);
    private final Clock clock = mock(Clock.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DeviceActivityAggregationScheduler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private DeviceActivityMetrics metrics;
    private DeviceActivityAggregationScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(STARTED_AT);
        metrics = new DeviceActivityMetrics(registry, clock);
        scheduler = new DeviceActivityAggregationScheduler(service, metrics, registry, clock);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
        registry.close();
    }

    @Test
    void 초기화_실패를_격리하고_자정_뒤_재시도에도_기동_시각을_전달한다() {
        // given
        doThrow(new IllegalStateException("private-device-data")).doNothing()
                .when(service).initialize(STARTED_AT);

        // when / then
        assertThatCode(scheduler::update).doesNotThrowAnyException();
        verify(service, never()).update();
        assertThat(활성_기기_수()).isNaN();
        assertThat(마지막_집계_시각()).isNaN();
        assertThat(실패_수()).isOne();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("기기 활동 집계에 실패했습니다");
            assertThat(event.getKeyValuePairs().toString()).contains("device_activity_aggregation_failed")
                    .doesNotContain("private-device-data");
            assertThat(event.getThrowableProxy()).isNull();
        });

        // when / then
        Instant retriedAt = STARTED_AT.plusSeconds(300);
        when(clock.instant()).thenReturn(retriedAt);
        when(service.update()).thenReturn(집계_결과(retriedAt, 0));
        scheduler.update();
        verify(service, times(2)).initialize(STARTED_AT);
        assertThat(활성_기기_수()).isZero();
        assertThat(마지막_집계_시각()).isEqualTo(retriedAt.getEpochSecond());
        assertThat(registry.get("pheeeew.activity.collection.started").gauge().value())
                .isEqualTo(STARTED_AT.getEpochSecond());
        assertThat(실패_수()).isOne();
    }

    @Test
    void 집계_실패는_이전_수치와_성공_시각을_유지하고_다음_성공에서만_교체한다() {
        // given
        Instant earlier = STARTED_AT;
        Instant retriedAt = STARTED_AT.plusSeconds(30);
        when(clock.instant()).thenReturn(retriedAt);
        metrics.update(집계_결과(earlier, 2));
        when(service.update()).thenThrow(new IllegalStateException("query failed"))
                .thenReturn(집계_결과(retriedAt, 3));

        // when / then
        assertThatCode(scheduler::update).doesNotThrowAnyException();
        assertThat(활성_기기_수()).isEqualTo(2);
        assertThat(마지막_집계_시각()).isEqualTo(earlier.getEpochSecond());
        assertThat(실패_수()).isOne();

        // when / then
        scheduler.update();
        assertThat(활성_기기_수()).isEqualTo(3);
        assertThat(마지막_집계_시각()).isEqualTo(retriedAt.getEpochSecond());
        assertThat(실패_수()).isOne();
        verify(service, times(2)).initialize(STARTED_AT);
    }

    private DeviceActivitySnapshot 집계_결과(Instant aggregatedAt, long count) {
        return DeviceActivitySnapshot.of(aggregatedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                aggregatedAt, STARTED_AT, List.of(DeviceActivityCount.of(DevicePlatform.ANDROID, count, count),
                        DeviceActivityCount.of(DevicePlatform.IOS, 0, 0)));
    }

    private double 활성_기기_수() {
        return registry.get("pheeeew.activity.dau").tag("platform", "android").gauge().value();
    }

    private double 마지막_집계_시각() {
        return registry.get("pheeeew.activity.last.aggregated").gauge().value();
    }

    private double 실패_수() {
        return registry.get("pheeeew.activity.aggregation.failures").counter().count();
    }
}
