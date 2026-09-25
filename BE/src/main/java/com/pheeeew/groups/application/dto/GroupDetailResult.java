package com.pheeeew.groups.application.dto;

public record GroupDetailResult(
        GroupResult group,
        GroupPressCountResult todayPresses,
        long weeklyScore,
        Integer weeklyRank
) {

    public static GroupDetailResult of(
            GroupResult group,
            GroupPressCountResult todayPresses,
            long weeklyScore,
            Integer weeklyRank
    ) {
        return new GroupDetailResult(group, todayPresses, weeklyScore, weeklyRank);
    }
}
