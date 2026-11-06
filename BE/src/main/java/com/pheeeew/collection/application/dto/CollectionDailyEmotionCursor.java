package com.pheeeew.collection.application.dto;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_CURSOR;

import com.pheeeew.collection.exception.CollectionException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record CollectionDailyEmotionCursor(UUID deviceId, LocalDate date,
        Instant snapshotAt, Instant lastCreatedAt, long lastId) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public static CollectionDailyEmotionCursor initial(UUID deviceId, LocalDate date, Instant snapshotAt) {
        return of(deviceId, date, snapshotAt, snapshotAt, Long.MAX_VALUE);
    }

    public static CollectionDailyEmotionCursor of(UUID deviceId, LocalDate date,
            Instant snapshotAt, Instant lastCreatedAt, long lastId) {
        return new CollectionDailyEmotionCursor(deviceId, date, snapshotAt, lastCreatedAt, lastId);
    }

    public void validateRequest(UUID requestedDeviceId, LocalDate requestedDate, Instant now) {
        if (!deviceId.equals(requestedDeviceId) || !date.equals(requestedDate)
                || snapshotAt.isAfter(now) || lastCreatedAt.isAfter(snapshotAt) || lastId < 1
                || lastCreatedAt.isBefore(date.atStartOfDay(KOREA).toInstant())
                || !lastCreatedAt.isBefore(date.plusDays(1).atStartOfDay(KOREA).toInstant())) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }

    public CollectionDailyEmotionCursor next(Instant createdAt, long id) {
        return of(deviceId, date, snapshotAt, createdAt, id);
    }
}
