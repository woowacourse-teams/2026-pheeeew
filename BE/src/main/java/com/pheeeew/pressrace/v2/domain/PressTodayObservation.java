package com.pheeeew.pressrace.v2.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PressTodayObservation(
        LocalDate date,
        Instant observedAt,
        List<PressTodayParticipant> participants,
        String token,
        long version,
        long nicknameVersion
) {
    public static PressTodayObservation of(
            LocalDate date,
            Instant observedAt,
            List<PressTodayParticipant> participants,
            String token,
            long version,
            long nicknameVersion
    ) {
        return new PressTodayObservation(date, observedAt, participants, token, version, nicknameVersion);
    }

    public static PressTodayObservation advance(PressTodayObservation previous, PressTodayRead read) {
        if (read.status() != PressReadStatus.READ && read.status() != PressReadStatus.UNCHANGED) {
            throw new IllegalArgumentException("참가자를 읽지 않은 결과로 관찰을 갱신할 수 없습니다.");
        }
        if (previous != null && read.date().isBefore(previous.date())) {
            throw new IllegalArgumentException("관찰 날짜는 뒤로 갈 수 없습니다.");
        }
        boolean sameDate = previous != null && previous.date().equals(read.date());
        if (read.status() == PressReadStatus.UNCHANGED) {
            if (!sameDate) {
                throw new IllegalArgumentException("같은 날짜의 직전 관찰이 없으면 변경 없음 결과를 쓸 수 없습니다.");
            }
            return of(previous.date(), later(previous.observedAt(), read.observedAt()), previous.participants(),
                    previous.token(), previous.version(), previous.nicknameVersion());
        }
        Instant observedAt = sameDate ? later(previous.observedAt(), read.observedAt()) : read.observedAt();
        return of(read.date(), observedAt, List.copyOf(read.participants()), read.token(), read.version(),
                read.nicknameVersion());
    }

    public Map<UUID, String> nicknames() {
        Map<UUID, String> nicknames = new HashMap<>();
        participants.forEach(participant -> nicknames.put(participant.devicePublicId(), participant.nickname()));
        return nicknames;
    }

    private static Instant later(Instant previous, Instant observed) {
        return observed.isAfter(previous) ? observed : previous;
    }
}
