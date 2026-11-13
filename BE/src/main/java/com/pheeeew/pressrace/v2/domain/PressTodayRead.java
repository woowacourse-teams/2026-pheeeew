package com.pheeeew.pressrace.v2.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PressTodayRead(
        PressReadStatus status,
        Instant observedAt,
        LocalDate date,
        String token,
        long version,
        long nicknameVersion,
        List<PressTodayParticipant> participants,
        Set<UUID> missingNicknames
) {
    public static PressTodayRead of(
            PressReadStatus status,
            Instant observedAt,
            LocalDate date,
            String token,
            long version,
            long nicknameVersion,
            List<PressTodayParticipant> participants,
            Set<UUID> missingNicknames
    ) {
        return new PressTodayRead(status, observedAt, date, token, version, nicknameVersion, participants,
                missingNicknames);
    }

    public PressTodayRead withFallbackNicknames(Map<UUID, String> nicknames) {
        return of(status, observedAt, date, null, version, nicknameVersion, fill(nicknames), Set.of());
    }

    private List<PressTodayParticipant> fill(Map<UUID, String> nicknames) {
        return participants.stream()
                .map(participant -> missingNicknames.contains(participant.devicePublicId())
                        ? participant.withNickname(nicknames.get(participant.devicePublicId()))
                        : participant)
                .toList();
    }
}
