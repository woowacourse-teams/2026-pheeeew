package com.pheeeew.pressevent.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PressEventRankingResult(
        Standing leader,
        List<Standing> items,
        String nextCursor,
        MyRank me,
        Instant rankedAt
) {

    public static PressEventRankingResult of(
            Standing leader, List<Standing> items, String nextCursor, MyRank me, Instant rankedAt
    ) {
        return new PressEventRankingResult(leader, items, nextCursor, me, rankedAt);
    }

    public record Standing(long rank, UUID publicId, String nickname, int bestScore, boolean mine, Instant achievedAt) {

        public static Standing of(long rank, UUID publicId, String nickname, int score, boolean mine, Instant achievedAt) {
            return new Standing(rank, publicId, nickname, score, mine, achievedAt);
        }
    }

    public record MyRank(int bestScore, long rank, Instant rankedAt) {

        public static MyRank of(int bestScore, long rank, Instant rankedAt) {
            return new MyRank(bestScore, rank, rankedAt);
        }
    }
}
