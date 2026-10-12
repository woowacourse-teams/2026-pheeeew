package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.util.List;

public record EmotionStatePressRankingResult(
        EmotionState state,
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        List<EmotionPressRankingItem> items,
        EmotionPressRankingItem me
) {
    public static EmotionStatePressRankingResult of(
            EmotionState state,
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            List<EmotionPressRankingItem> items,
            EmotionPressRankingItem me
    ) {
        return new EmotionStatePressRankingResult(state, weeksAgo, startAt, endAt, List.copyOf(items), me);
    }
}
