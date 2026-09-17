package com.pheeeew.activity.infra.metrics;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.device.domain.DevicePlatform;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToLongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
public class DeviceActivityMetrics {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    // 스케줄러가 교체한 불변 결과를 지표 수집 스레드에서도 읽을 수 있도록 한다.
    private volatile DeviceActivitySnapshot snapshot;

    public DeviceActivityMetrics(MeterRegistry registry, Clock clock) {
        this.clock = clock;
        for (DevicePlatform platform : DevicePlatform.values()) {
            String label = platform.name().toLowerCase(Locale.ROOT);
            Gauge.builder("pheeeew.activity.dau", this, metrics -> metrics.count(platform, DeviceActivityCount::dau))
                    .description("Distinct active devices on the current KST date; NaN until available")
                    .tag("platform", label)
                    .register(registry);
            Gauge.builder("pheeeew.activity.mau", this, metrics -> metrics.count(platform, DeviceActivityCount::mau))
                    .description("Distinct active devices over 30 KST dates ending today; NaN until available")
                    .tag("platform", label)
                    .register(registry);
        }

        Gauge.builder("pheeeew.activity.last.aggregated", this, DeviceActivityMetrics::lastAggregatedAt)
                .description("Reference Unix timestamp of the last successful aggregation; NaN until available")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("pheeeew.activity.collection.started", this, DeviceActivityMetrics::collectionStartedAt)
                .description("Collection start Unix timestamp; NaN until the first successful aggregation")
                .baseUnit("seconds")
                .register(registry);
    }

    public void update(DeviceActivitySnapshot snapshot) {
        // 실패 시에는 호출하지 않고, 마지막 성공 결과와 시각을 그대로 유지한다.
        this.snapshot = Objects.requireNonNull(snapshot);
    }

    private double count(DevicePlatform platform, ToLongFunction<DeviceActivityCount> value) {
        DeviceActivitySnapshot current = snapshot;
        LocalDate today = clock.instant().atZone(ACTIVITY_ZONE).toLocalDate();
        // 미집계와 실제 0명을 구분하고, 자정 이후 전날 집계를 오늘 수치로 노출하지 않는다.
        if (current == null || !current.activityDate().equals(today)) {
            return Double.NaN;
        }

        for (DeviceActivityCount count : current.counts()) {
            if (count.platform() == platform) {
                return value.applyAsLong(count);
            }
        }
        return Double.NaN;
    }

    private double lastAggregatedAt() {
        DeviceActivitySnapshot current = snapshot;
        if (current == null) {
            return Double.NaN;
        }
        return current.aggregatedAt().getEpochSecond();
    }

    private double collectionStartedAt() {
        DeviceActivitySnapshot current = snapshot;
        if (current == null) {
            return Double.NaN;
        }
        return current.collectionStartedAt().getEpochSecond();
    }
}
