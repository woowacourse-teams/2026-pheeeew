package com.pheeeew.pressrace.fixture;

import com.pheeeew.pressrace.domain.PressRanking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

public final class PressRankingFixture {

    public static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    public static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    public static final UUID OBSERVER_ID = new UUID(1, 0);

    private PressRankingFixture() {
    }

    public static PressRanking.Participant 참가자(long id, long score) {
        return 참가자(id, new UUID(0, id), score, NOW);
    }

    public static PressRanking.Participant 참가자(long id, UUID publicId, long score, Instant appliedAt) {
        return PressRanking.Participant.of(new UUID(1, id), publicId, "별" + id, score, appliedAt);
    }

    public static List<PressRanking.Participant> 점수가_내림차순인_참가자들(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> 참가자(id, count + 1L - id)).toList();
    }

    public static PressRanking 순위(List<PressRanking.Participant> participants) {
        return PressRanking.of(DATE, NOW, participants);
    }
}
