package com.pheeeew.groups.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;

public record GroupPressCountResult(Map<EmotionState, Long> counts, long total) {

    public static GroupPressCountResult from(Map<EmotionState, Long> counts) {
        return new GroupPressCountResult(
                Map.copyOf(counts),
                counts.values().stream().mapToLong(Long::longValue).sum()
        );
    }
}
