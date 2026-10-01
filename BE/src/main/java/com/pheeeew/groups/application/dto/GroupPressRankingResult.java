package com.pheeeew.groups.application.dto;

import java.time.Instant;
import java.util.List;

public record GroupPressRankingResult(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<GroupPressRankingItem> items
) {
    public static GroupPressRankingResult of(
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            boolean hasPrevious,
            List<GroupPressRankingItem> items
    ) {
        return new GroupPressRankingResult(weeksAgo, startAt, endAt, hasPrevious, List.copyOf(items));
    }
}
