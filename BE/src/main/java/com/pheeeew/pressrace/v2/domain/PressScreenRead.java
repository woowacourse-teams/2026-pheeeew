package com.pheeeew.pressrace.v2.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PressScreenRead(
        PressReadStatus status,
        Instant observedAt,
        LocalDate date,
        int activeCount,
        int nearbyStart,
        long nearbyFirstRank,
        List<PressTodayParticipant> nearby,
        List<PressTodayParticipant> top,
        Set<UUID> missingNicknames
) {
    public static PressScreenRead of(
            PressReadStatus status,
            Instant observedAt,
            LocalDate date,
            int activeCount,
            int nearbyStart,
            long nearbyFirstRank,
            List<PressTodayParticipant> nearby,
            List<PressTodayParticipant> top,
            Set<UUID> missingNicknames
    ) {
        return new PressScreenRead(status, observedAt, date, activeCount, nearbyStart, nearbyFirstRank, nearby, top,
                missingNicknames);
    }

    public PressScreenRead withFallbackNicknames(Map<UUID, String> nicknames) {
        return of(status, observedAt, date, activeCount, nearbyStart, nearbyFirstRank, fill(nearby, nicknames),
                fill(top, nicknames), Set.of());
    }

    private List<PressTodayParticipant> fill(List<PressTodayParticipant> participants, Map<UUID, String> nicknames) {
        return participants.stream()
                .map(participant -> missingNicknames.contains(participant.devicePublicId())
                        ? participant.withNickname(nicknames.get(participant.devicePublicId()))
                        : participant)
                .toList();
    }
}
