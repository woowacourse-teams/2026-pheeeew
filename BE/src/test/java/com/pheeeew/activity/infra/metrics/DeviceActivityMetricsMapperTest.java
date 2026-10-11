package com.pheeeew.activity.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Count;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Platform;
import com.pheeeew.device.domain.DevicePlatform;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceActivityMetricsMapperTest {

    private static final LocalDate ACTIVITY_DATE = LocalDate.of(2026, 9, 17);
    private static final Instant AGGREGATED_AT = Instant.parse("2026-09-17T15:00:00.123456789Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T03:00:00Z");

    @Test
    void 날짜와_시각_수치_및_중복을_포함한_플랫폼_순서를_그대로_변환한다() {
        // given
        DeviceActivitySnapshot source = DeviceActivitySnapshot.of(ACTIVITY_DATE, AGGREGATED_AT, STARTED_AT, List.of(
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0),
                DeviceActivityCount.of(DevicePlatform.ANDROID, 2, Long.MAX_VALUE),
                DeviceActivityCount.of(DevicePlatform.IOS, 3, 9)));

        // when
        DeviceActivityMetricSnapshot actual = DeviceActivityMetricsMapper.from(source);

        // then
        assertThat(actual.activityDate()).isEqualTo(ACTIVITY_DATE);
        assertThat(actual.aggregatedAt()).isEqualTo(AGGREGATED_AT);
        assertThat(actual.collectionStartedAt()).isEqualTo(STARTED_AT);
        assertThat(actual.counts()).containsExactly(
                Count.of(Platform.IOS, 0, 0),
                Count.of(Platform.ANDROID, 2, Long.MAX_VALUE),
                Count.of(Platform.IOS, 3, 9));
        assertThat(source.counts()).containsExactly(
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0),
                DeviceActivityCount.of(DevicePlatform.ANDROID, 2, Long.MAX_VALUE),
                DeviceActivityCount.of(DevicePlatform.IOS, 3, 9));
    }

    @Test
    void 누락된_플랫폼을_0명으로_추가하지_않는다() {
        // given
        DeviceActivitySnapshot source = DeviceActivitySnapshot.of(ACTIVITY_DATE, AGGREGATED_AT, STARTED_AT,
                List.of(DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 7)));

        // when
        DeviceActivityMetricSnapshot actual = DeviceActivityMetricsMapper.from(source);

        // then
        assertThat(actual.counts()).containsExactly(Count.of(Platform.ANDROID, 0, 7));
    }

    @Test
    void 빈_집계_결과도_그대로_유지한다() {
        // given
        DeviceActivitySnapshot source = DeviceActivitySnapshot.of(ACTIVITY_DATE, AGGREGATED_AT, STARTED_AT, List.of());

        // when
        DeviceActivityMetricSnapshot actual = DeviceActivityMetricsMapper.from(source);

        // then
        assertThat(actual.counts()).isEmpty();
    }
}
