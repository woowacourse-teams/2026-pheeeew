package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;

public record EmotionPressResult(String regionCode, Map<EmotionState, Long> counts, long total) {

    public static EmotionPressResult of(String regionCode, Map<EmotionState, Long> counts) {
        return new EmotionPressResult(
                regionCode,
                Map.copyOf(counts),
                counts.values().stream().mapToLong(Long::longValue).sum()
        );
    }
}
