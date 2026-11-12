package com.pheeeew.climb.domain;

import java.time.Instant;
import java.util.UUID;

/** 순위표 한 줄. 한 번의 등반(run)이 한 줄이다. 완주하지 않았으면 finishSeconds 가 null 이다. */
public record ClimbRankEntry(UUID runId, UUID guestId, String tag, String nickname, String character,
                             int best, Instant bestAt, Integer finishSeconds) {

    public static ClimbRankEntry of(UUID runId, UUID guestId, String tag, String nickname, String character,
            int best, Instant bestAt, Integer finishSeconds) {
        return new ClimbRankEntry(runId, guestId, tag, nickname, character, best, bestAt, finishSeconds);
    }

    public boolean finished() {
        return finishSeconds != null;
    }

    public ClimbRankEntry withNickname(String nickname) {
        return of(runId, guestId, tag, nickname, character, best, bestAt, finishSeconds);
    }
}
