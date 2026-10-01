package com.pheeeew.groups.application.dto;

import java.time.Instant;
import java.util.List;

public record GroupStatePressRankingResult(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<GroupStatePressRankingGroup> states
) {
    public static GroupStatePressRankingResult of(
            int weeksAgo,
            Instant startAt,
            Instant endAt,
            boolean hasPrevious,
            List<GroupStatePressRankingGroup> states
    ) {
        return new GroupStatePressRankingResult(weeksAgo, startAt, endAt, hasPrevious, List.copyOf(states));
    }
}
