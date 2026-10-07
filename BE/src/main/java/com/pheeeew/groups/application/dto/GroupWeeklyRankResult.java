package com.pheeeew.groups.application.dto;

public record GroupWeeklyRankResult(
        long stampCount,
        Integer stampRank,
        long pressCount,
        Integer pressRank
) {
    public static GroupWeeklyRankResult of(GroupRankingItem stamp, GroupRankingItem press) {
        return new GroupWeeklyRankResult(
                stamp == null ? 0 : stamp.score(),
                stamp == null ? null : stamp.rank(),
                press == null ? 0 : press.score(),
                press == null ? null : press.rank()
        );
    }
}
