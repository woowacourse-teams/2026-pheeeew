package com.pheeeew.activity.infra.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DeviceActivityMetricSnapshot(
        LocalDate activityDate,
        Instant aggregatedAt,
        Instant collectionStartedAt,
        List<Count> counts
) {

    public DeviceActivityMetricSnapshot {
        counts = List.copyOf(counts);
    }

    public static DeviceActivityMetricSnapshot of(
            LocalDate activityDate,
            Instant aggregatedAt,
            Instant collectionStartedAt,
            List<Count> counts
    ) {
        return new DeviceActivityMetricSnapshot(activityDate, aggregatedAt, collectionStartedAt, counts);
    }

    public record Count(Platform platform, long dau, long mau) {

        public static Count of(Platform platform, long dau, long mau) {
            return new Count(platform, dau, mau);
        }
    }

    public enum Platform {
        ANDROID,
        IOS
    }
}
