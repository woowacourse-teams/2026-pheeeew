package com.pheeeew.groups.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.util.List;

public record GroupStatePressRankingResult(
        EmotionState state,
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<GroupPressRankingItem> items
) {
    public static GroupStatePressRankingResult of(
            EmotionState state,
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            boolean hasPrevious,
            List<GroupPressRankingItem> items
    ) {
        return new GroupStatePressRankingResult(state, weeksAgo, startAt, endAt, hasPrevious, List.copyOf(items));
    }
}
