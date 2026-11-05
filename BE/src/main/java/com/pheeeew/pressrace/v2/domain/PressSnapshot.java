package com.pheeeew.pressrace.v2.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 한 번의 DB 관찰로 만든 오늘의 순위.
 * 표시 순서는 점수 내림차순, 공개 ID 오름차순이고 순위는 점수만으로 정한다(1, 2, 2, 4).
 */
public class PressSnapshot {

    private static final Duration ACTIVE_WINDOW = Duration.ofSeconds(5);
    private static final int TOP_SIZE = 100;
    private static final int NEARBY_SIZE = 3;
    private static final int LEADER_SIZE = 2;

    private final LocalDate date;
    private final Instant generatedAt;
    private final int activeCount;
    private final List<PressRanker> rankers;
    private final Map<UUID, Integer> positions;

    private PressSnapshot(LocalDate date, Instant generatedAt, int activeCount, List<PressRanker> rankers,
            Map<UUID, Integer> positions) {
        this.date = date;
        this.generatedAt = generatedAt;
        this.activeCount = activeCount;
        this.rankers = rankers;
        this.positions = positions;
    }

    public static PressSnapshot of(LocalDate date, Instant generatedAt, List<PressParticipant> participants) {
        // UUID.compareTo 는 signed 비교라 표준 문자열(unsigned) 순서로 정렬한다.
        List<PressParticipant> ordered = participants.stream()
                .sorted(Comparator.comparingLong(PressParticipant::score).reversed()
                        .thenComparing(participant -> participant.publicId().toString()))
                .toList();

        List<PressRanker> rankers = new ArrayList<>();
        Map<UUID, Integer> positions = new HashMap<>();
        long rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            PressParticipant participant = ordered.get(index);
            if (index == 0 || participant.score() != ordered.get(index - 1).score()) {
                rank = index + 1L;
            }
            rankers.add(PressRanker.of(participant, rank));
            positions.put(participant.devicePublicId(), index);
        }

        int activeCount = (int) participants.stream()
                .filter(participant -> isActive(participant, generatedAt))
                .count();

        return new PressSnapshot(date, generatedAt, activeCount, List.copyOf(rankers), Map.copyOf(positions));
    }

    public LocalDate date() {
        return date;
    }

    public PressScreen findScreen(UUID devicePublicId, PressView view) {
        Integer index = positions.get(devicePublicId);
        PressRanker me = index == null ? PressRanker.absent() : rankers.get(index);
        List<PressRanker> top100 = view == PressView.ALL ? rankers.subList(0, Math.min(TOP_SIZE, rankers.size()))
                : List.of();

        return PressScreen.of(date, generatedAt, activeCount, view, me, findNearby(index), top100);
    }

    private static boolean isActive(PressParticipant participant, Instant now) {
        return participant.lastAppliedAt().plus(ACTIVE_WINDOW).isAfter(now);
    }

    private List<PressRanker> findNearby(Integer index) {
        if (index == null) {
            return rankers.subList(0, Math.min(LEADER_SIZE, rankers.size()));
        }

        int start = Math.clamp(index - 1, 0, Math.max(rankers.size() - NEARBY_SIZE, 0));
        return rankers.subList(start, Math.min(start + NEARBY_SIZE, rankers.size()));
    }
}
