package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record PressDaySnapshot(
        long deviceId,
        LocalDate date,
        Map<EmotionState, Long> counts,
        long total,
        Instant lastAppliedAt,
        long version
) {
    public static PressDaySnapshot of(
            long deviceId,
            LocalDate date,
            Map<EmotionState, Long> counts,
            long total,
            Instant lastAppliedAt,
            long version
    ) {
        return new PressDaySnapshot(deviceId, date, counts, total, lastAppliedAt, version);
    }
}
