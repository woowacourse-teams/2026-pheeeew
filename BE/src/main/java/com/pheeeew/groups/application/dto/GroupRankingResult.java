package com.pheeeew.groups.application.dto;

import java.time.Instant;
import java.util.List;

public record GroupRankingResult(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<GroupRankingItem> items
) {

    public static GroupRankingResult of(
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            boolean hasPrevious,
            List<GroupRankingItem> items
    ) {
        return new GroupRankingResult(weeksAgo, startAt, endAt, hasPrevious, List.copyOf(items));
    }
}
