package com.pheeeew.groups.application.dto;

public record GroupWeeklyRankResult(
        long stampCount,
        Integer stampRank,
        long emotionPressCount,
        Integer emotionPressRank
) {
    public static GroupWeeklyRankResult of(GroupRankingItem stamp, GroupRankingItem emotionPress) {
        return new GroupWeeklyRankResult(
                stamp == null ? 0 : stamp.score(),
                stamp == null ? null : stamp.rank(),
                emotionPress == null ? 0 : emotionPress.score(),
                emotionPress == null ? null : emotionPress.rank()
        );
    }
}
