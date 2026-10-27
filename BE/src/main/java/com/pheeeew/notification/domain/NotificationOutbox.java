package com.pheeeew.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record NotificationOutbox(
        Long id,
        NotificationOutboxType type,
        Long deviceId,
        LocalDate targetDate,
        Integer previousRank,
        Integer currentRank,
        int attemptCount,
        Instant expiresAt,
        UUID claimToken
) {
    public static NotificationOutbox of(
            Long id,
            NotificationOutboxType type,
            Long deviceId,
            LocalDate targetDate,
            Integer previousRank,
            Integer currentRank,
            int attemptCount,
            Instant expiresAt,
            UUID claimToken
    ) {
        return new NotificationOutbox(
                id, type, deviceId, targetDate, previousRank, currentRank, attemptCount, expiresAt, claimToken);
    }

    public PushMessage message() {
        if (type.isCollection()) {
            return PushMessage.of(type.getTitle(), type.getBody(), Map.of(
                    "type", type.name(),
                    "date", targetDate.toString()
            ));
        }
        return PushMessage.of(type.getTitle(), type.getBody().formatted(previousRank, currentRank), Map.of(
                "type", type.name(),
                "date", targetDate.toString(),
                "previousRank", String.valueOf(previousRank),
                "currentRank", String.valueOf(currentRank)
        ));
    }
}
