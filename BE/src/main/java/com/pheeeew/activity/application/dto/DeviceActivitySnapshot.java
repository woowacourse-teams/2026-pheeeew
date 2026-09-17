package com.pheeeew.activity.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DeviceActivitySnapshot(
        LocalDate activityDate,
        Instant aggregatedAt,
        Instant collectionStartedAt,
        List<DeviceActivityCount> counts
) {

    public DeviceActivitySnapshot {
        counts = List.copyOf(counts);
    }

    public static DeviceActivitySnapshot of(
            LocalDate activityDate,
            Instant aggregatedAt,
            Instant collectionStartedAt,
            List<DeviceActivityCount> counts
    ) {
        return new DeviceActivitySnapshot(activityDate, aggregatedAt, collectionStartedAt, counts);
    }
}
