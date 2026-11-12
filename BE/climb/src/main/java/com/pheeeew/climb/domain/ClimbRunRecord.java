package com.pheeeew.climb.domain;

import java.time.Instant;
import java.util.UUID;

/** climb_run 한 줄. */
public record ClimbRunRecord(UUID id, UUID guestId, String mapVersion, String character, int best, Instant bestAt,
                             int height, int x, int falls, int playSeconds, Instant finishedAt, Integer finishSeconds,
                             boolean suspect, Instant startedAt, Instant updatedAt) {

    public static ClimbRunRecord of(UUID id, UUID guestId, String mapVersion, String character, int best,
            Instant bestAt, int height, int x, int falls, int playSeconds, Instant finishedAt, Integer finishSeconds,
            boolean suspect, Instant startedAt, Instant updatedAt) {
        return new ClimbRunRecord(id, guestId, mapVersion, character, best, bestAt, height, x, falls, playSeconds,
                finishedAt, finishSeconds, suspect, startedAt, updatedAt);
    }
}
