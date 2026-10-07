package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Map;

public record EmotionPressDailyResult(LocalDate pressDate, Map<EmotionState, Long> counts, long total) {

    public static EmotionPressDailyResult of(LocalDate pressDate, Map<EmotionState, Long> counts) {
        return new EmotionPressDailyResult(
                pressDate,
                Map.copyOf(counts),
                counts.values().stream().mapToLong(Long::longValue).sum()
        );
    }
}
