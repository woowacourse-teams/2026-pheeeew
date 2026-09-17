package com.pheeeew.activity.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.device.domain.DevicePlatform;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceActivityMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-17T14:59:59Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final Clock clock = mock(Clock.class);
    private DeviceActivityMetrics metrics;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        metrics = new DeviceActivityMetrics(registry, clock);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void 최초_집계_전에는_0명이나_임의의_시각을_노출하지_않는다() {
        // given / when
        String exported = registry.scrape();

        // then
        assertThat(registry.getMeters()).hasSize(6);
        assertThat(exported.lines().filter(line -> !line.startsWith("#")).toList())
                .hasSize(6).allSatisfy(line -> assertThat(line).endsWith(" NaN"));
        assertThat(exported).contains("pheeeew_activity_dau{platform=\"android\"} NaN",
                "pheeeew_activity_last_aggregated_seconds NaN",
                "pheeeew_activity_collection_started_seconds NaN");
    }

    @Test
    void 플랫폼별_최신_수치로_교체하고_정상_집계된_0명은_그대로_노출한다() {
        // given
        metrics.update(DeviceActivitySnapshot.of(TODAY, NOW.minusSeconds(300), STARTED_AT, List.of(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 5, 20),
                DeviceActivityCount.of(DevicePlatform.IOS, 3, 10))));

        // when
        metrics.update(DeviceActivitySnapshot.of(TODAY, NOW, STARTED_AT, List.of(
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0),
                DeviceActivityCount.of(DevicePlatform.ANDROID, 2, 8))));

        // then
        assertThat(활성_기기_수("dau", "android")).isEqualTo(2);
        assertThat(활성_기기_수("mau", "android")).isEqualTo(8);
        assertThat(활성_기기_수("dau", "ios")).isZero();
        assertThat(활성_기기_수("mau", "ios")).isZero();
        assertThat(시각("last.aggregated")).isEqualTo(NOW.getEpochSecond());
        assertThat(시각("collection.started")).isEqualTo(STARTED_AT.getEpochSecond());
        assertThat(registry.getMeters()).hasSize(6);
        assertThat(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream()).distinct().toList())
                .containsExactlyInAnyOrder(Tag.of("platform", "android"), Tag.of("platform", "ios"));
    }

    @Test
    void 갱신이_없으면_성공_시각을_유지하고_KST_자정부터는_이전_활성_수를_숨긴다() {
        // given
        Instant aggregatedAt = NOW.minusSeconds(300);
        metrics.update(DeviceActivitySnapshot.of(TODAY, aggregatedAt, STARTED_AT, List.of(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 2, 8),
                DeviceActivityCount.of(DevicePlatform.IOS, 1, 4))));

        // when / then: 새 성공 결과가 없으면 수치와 성공 시각을 유지한다.
        assertThat(활성_기기_수("dau", "android")).isEqualTo(2);
        assertThat(시각("last.aggregated")).isEqualTo(aggregatedAt.getEpochSecond());

        // when / then: KST 날짜가 바뀌면 DAU와 MAU 모두 새 기준일의 집계를 기다린다.
        Instant midnight = NOW.plusSeconds(1);
        when(clock.instant()).thenReturn(midnight);
        for (String platform : List.of("android", "ios")) {
            assertThat(활성_기기_수("dau", platform)).isNaN();
            assertThat(활성_기기_수("mau", platform)).isNaN();
        }
        assertThat(시각("last.aggregated")).isEqualTo(aggregatedAt.getEpochSecond());
        assertThat(시각("collection.started")).isEqualTo(STARTED_AT.getEpochSecond());

        // when / then: 새 날짜 집계가 성공하면 다시 수치를 노출한다.
        metrics.update(DeviceActivitySnapshot.of(TODAY.plusDays(1), midnight, STARTED_AT, List.of(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 7),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 3))));
        assertThat(활성_기기_수("dau", "android")).isZero();
        assertThat(활성_기기_수("mau", "android")).isEqualTo(7);
        assertThat(시각("last.aggregated")).isEqualTo(midnight.getEpochSecond());
        assertThat(registry.getMeters()).hasSize(6);
    }

    @Test
    void 결과에_없는_플랫폼을_0명으로_간주하지_않는다() {
        // given / when
        metrics.update(DeviceActivitySnapshot.of(TODAY, NOW, STARTED_AT,
                List.of(DeviceActivityCount.of(DevicePlatform.ANDROID, 1, 2))));

        // then
        assertThat(활성_기기_수("dau", "android")).isOne();
        assertThat(활성_기기_수("dau", "ios")).isNaN();
        assertThat(활성_기기_수("mau", "ios")).isNaN();
    }

    private double 활성_기기_수(String period, String platform) {
        return registry.get("pheeeew.activity." + period).tag("platform", platform).gauge().value();
    }

    private double 시각(String name) {
        return registry.get("pheeeew.activity." + name).gauge().value();
    }
}
