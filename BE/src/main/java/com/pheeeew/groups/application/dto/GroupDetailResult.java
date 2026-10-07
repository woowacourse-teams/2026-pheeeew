package com.pheeeew.groups.application.dto;

public record GroupDetailResult(
        GroupResult group,
        GroupPressCountResult todayPresses,
        GroupPressCountResult weeklyPresses,
        long weeklyScore,
        Integer weeklyRank,
        Integer weeklyPressRank
) {
    public static GroupDetailResult of(
            GroupResult group,
            GroupPressCountResult todayPresses,
            GroupPressCountResult weeklyPresses,
            long weeklyScore,
            Integer weeklyRank,
            Integer weeklyPressRank
    ) {
        return new GroupDetailResult(group, todayPresses, weeklyPresses, weeklyScore, weeklyRank, weeklyPressRank);
    }
}
