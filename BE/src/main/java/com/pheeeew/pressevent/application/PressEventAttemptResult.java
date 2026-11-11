package com.pheeeew.pressevent.application;

import java.time.Instant;
import java.util.UUID;

public record PressEventAttemptResult(
        UUID attemptId,
        int total,
        int bestScore,
        long rank,
        Instant rankedAt,
        boolean replayed
) {

    public static PressEventAttemptResult of(
            UUID attemptId, int total, int bestScore, long rank, Instant rankedAt, boolean replayed
    ) {
        return new PressEventAttemptResult(attemptId, total, bestScore, rank, rankedAt, replayed);
    }
}
