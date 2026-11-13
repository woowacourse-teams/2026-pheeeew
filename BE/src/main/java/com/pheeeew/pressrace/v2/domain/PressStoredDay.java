package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record PressStoredDay(
        UUID devicePublicId,
        LocalDate date,
        Map<EmotionState, Long> counts,
        long total,
        Instant lastAppliedAt,
        UUID publicId,
        long version
) {
    public static PressStoredDay of(
            UUID devicePublicId,
            LocalDate date,
            Map<EmotionState, Long> counts,
            long total,
            Instant lastAppliedAt,
            UUID publicId,
            long version
    ) {
        return new PressStoredDay(devicePublicId, date, counts, total, lastAppliedAt, publicId, version);
    }

    public String dirtyMember() {
        return date + ":" + devicePublicId;
    }
}
