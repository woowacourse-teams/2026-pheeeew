package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;

public record EmotionPressResult(Map<EmotionState, Long> counts, long total) {

    public static EmotionPressResult from(Map<EmotionState, Long> counts) {
        return new EmotionPressResult(
                Map.copyOf(counts),
                counts.values().stream().mapToLong(Long::longValue).sum()
        );
    }
}
