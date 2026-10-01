package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupStatePressRankingResult;
import java.time.Instant;
import java.util.List;

public record GroupStatePressRankingResponse(
        EmotionState state,
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<GroupRankingResponse.Item> items
) {
    public static GroupStatePressRankingResponse from(GroupStatePressRankingResult result) {
        return new GroupStatePressRankingResponse(
                result.state(),
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.hasPrevious(),
                result.items().stream().map(GroupRankingResponse.Item::from).toList()
        );
    }
}
