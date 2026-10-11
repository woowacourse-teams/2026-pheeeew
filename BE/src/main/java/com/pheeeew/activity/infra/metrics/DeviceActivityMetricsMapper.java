package com.pheeeew.activity.infra.metrics;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Count;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Platform;
import java.util.List;

public final class DeviceActivityMetricsMapper {

    private DeviceActivityMetricsMapper() {
    }

    public static DeviceActivityMetricSnapshot from(DeviceActivitySnapshot snapshot) {
        List<Count> counts = snapshot.counts().stream()
                .map(DeviceActivityMetricsMapper::toCount)
                .toList();

        return DeviceActivityMetricSnapshot.of(
                snapshot.activityDate(), snapshot.aggregatedAt(), snapshot.collectionStartedAt(), counts);
    }

    private static Count toCount(DeviceActivityCount count) {
        Platform platform = switch (count.platform()) {
            case ANDROID -> Platform.ANDROID;
            case IOS -> Platform.IOS;
        };
        return Count.of(platform, count.dau(), count.mau());
    }
}
