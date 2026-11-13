package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public record PressTodayParticipant(
        UUID devicePublicId,
        UUID publicId,
        String nickname,
        long total,
        Instant lastAppliedAt,
        Map<EmotionState, Long> counts
) {
    private static final Comparator<Map.Entry<EmotionState, Long>> REPRESENTATIVE_ORDER =
            Map.Entry.<EmotionState, Long>comparingByValue()
                    .thenComparing(Map.Entry.comparingByKey(Comparator.comparing(EmotionState::name).reversed()));

    public static PressTodayParticipant of(
            UUID devicePublicId,
            UUID publicId,
            String nickname,
            long total,
            Instant lastAppliedAt,
            Map<EmotionState, Long> counts
    ) {
        return new PressTodayParticipant(devicePublicId, publicId, nickname, total, lastAppliedAt, counts);
    }

    public PressTodayParticipant withNickname(String nickname) {
        return of(devicePublicId, publicId, nickname, total, lastAppliedAt, counts);
    }

    public EmotionState representativeState() {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max(REPRESENTATIVE_ORDER)
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
