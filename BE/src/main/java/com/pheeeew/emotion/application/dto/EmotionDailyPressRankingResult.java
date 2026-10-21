package com.pheeeew.emotion.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmotionDailyPressRankingResult(
        LocalDate date,
        Instant startAt,
        Instant endAt,
        List<EmotionPressRankingItem> items,
        EmotionPressRankingItem me
) {
    public static EmotionDailyPressRankingResult of(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            List<EmotionPressRankingItem> items,
            EmotionPressRankingItem me
    ) {
        return new EmotionDailyPressRankingResult(date, startAt, endAt, List.copyOf(items), me);
    }
}
