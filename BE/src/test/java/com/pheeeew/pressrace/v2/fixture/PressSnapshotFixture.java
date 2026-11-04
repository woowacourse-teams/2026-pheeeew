package com.pheeeew.pressrace.v2.fixture;

import com.pheeeew.pressrace.v2.domain.PressParticipant;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

public final class PressSnapshotFixture {

    public static final LocalDate DATE = LocalDate.of(2026, 11, 4);
    public static final Instant NOW = Instant.parse("2026-11-04T02:00:00Z");

    private PressSnapshotFixture() {
    }

    public static UUID 기기(long id) {
        return new UUID(1, id);
    }

    public static PressParticipant 참가자(long id, long score) {
        return 참가자(id, new UUID(0, id), score, NOW);
    }

    public static PressParticipant 참가자(long id, UUID publicId, long score, Instant lastAppliedAt) {
        return PressParticipant.of(기기(id), publicId, "별" + id, score, lastAppliedAt);
    }

    public static List<PressParticipant> 점수가_내림차순인_참가자들(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> 참가자(id, count + 1L - id)).toList();
    }

    public static PressSnapshot 순위(List<PressParticipant> participants) {
        return PressSnapshot.of(DATE, NOW, participants);
    }
}
