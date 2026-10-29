package com.pheeeew.emotion.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmotionDailyPressRankingResult(
        LocalDate date,
        Instant startAt,
        Instant endAt,
        List<EmotionDailyPressRankingItem> items,
        EmotionDailyPressRankingItem me
) {
    public static EmotionDailyPressRankingResult of(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            List<EmotionDailyPressRankingItem> items,
            EmotionDailyPressRankingItem me
    ) {
        return new EmotionDailyPressRankingResult(date, startAt, endAt, List.copyOf(items), me);
    }
}
