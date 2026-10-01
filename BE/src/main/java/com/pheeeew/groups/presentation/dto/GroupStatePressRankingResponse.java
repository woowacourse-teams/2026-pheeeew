package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupStatePressRankingGroup;
import com.pheeeew.groups.application.dto.GroupStatePressRankingResult;
import java.time.Instant;
import java.util.List;

public record GroupStatePressRankingResponse(
        int weeksAgo,
        Instant startAt,
        Instant endAt,
        boolean hasPrevious,
        List<StateRanking> states
) {
    public static GroupStatePressRankingResponse from(GroupStatePressRankingResult result) {
        return new GroupStatePressRankingResponse(
                result.weeksAgo(),
                result.startAt(),
                result.endAt(),
                result.hasPrevious(),
                result.states().stream().map(StateRanking::from).toList()
        );
    }

    public record StateRanking(EmotionState state, List<GroupRankingResponse.Item> items) {

        public static StateRanking from(GroupStatePressRankingGroup group) {
            return new StateRanking(
                    group.state(),
                    group.items().stream().map(GroupRankingResponse.Item::from).toList()
            );
        }
    }
}
