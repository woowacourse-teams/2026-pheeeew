package com.pheeeew.emotion.application.dto;

import java.time.Instant;
import java.util.List;

public record EmotionPressRankingResult(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        List<EmotionPressRankingItem> items,
        EmotionPressRankingItem me
) {
    public static EmotionPressRankingResult of(
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            List<EmotionPressRankingItem> items,
            EmotionPressRankingItem me
    ) {
        return new EmotionPressRankingResult(weeksAgo, startAt, endAt, List.copyOf(items), me);
    }
}
