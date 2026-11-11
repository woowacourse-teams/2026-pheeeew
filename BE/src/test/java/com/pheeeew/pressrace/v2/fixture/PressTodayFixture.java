package com.pheeeew.pressrace.v2.fixture;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressReadStatus;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.v2.domain.PressTodayRead;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PressTodayFixture {

    public static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    public static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    public static final String TOKEN = "seed-token";

    private PressTodayFixture() {
    }

    public static PressTodayParticipant 참가자(Map<EmotionState, Long> counts) {
        return 참가자(new UUID(1, 1), "별", counts);
    }

    public static PressTodayParticipant 참가자(UUID devicePublicId, String nickname, Map<EmotionState, Long> counts) {
        Map<EmotionState, Long> filled = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            filled.put(state, counts.getOrDefault(state, 0L));
        }
        long total = filled.values().stream().mapToLong(Long::longValue).sum();
        return PressTodayParticipant.of(devicePublicId, UUID.randomUUID(), nickname, total, NOW, Map.copyOf(filled));
    }

    public static PressTodayRead 전체_읽기(LocalDate date, Instant observedAt, List<PressTodayParticipant> participants) {
        return PressTodayRead.of(PressReadStatus.READ, observedAt, date, TOKEN, 1, 0, participants, Set.of());
    }

    public static PressTodayRead 변경_없음(LocalDate date, Instant observedAt) {
        return PressTodayRead.of(PressReadStatus.UNCHANGED, observedAt, date, TOKEN, 1, 0, List.of(), Set.of());
    }

    public static PressStoredDay 하루_기록(UUID devicePublicId, LocalDate date, Map<EmotionState, Long> counts,
            Instant lastAppliedAt, long version) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return PressStoredDay.of(devicePublicId, date, counts, total, lastAppliedAt, UUID.randomUUID(), version);
    }
}
