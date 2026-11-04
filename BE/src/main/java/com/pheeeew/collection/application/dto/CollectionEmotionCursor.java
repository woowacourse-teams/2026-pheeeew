package com.pheeeew.collection.application.dto;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_CURSOR;

import com.pheeeew.collection.exception.CollectionException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record CollectionEmotionCursor(UUID deviceId, UUID cardId, LocalDate date,
        Instant snapshotAt, Instant lastCreatedAt, long lastId) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public static CollectionEmotionCursor initial(UUID deviceId, UUID cardId, LocalDate date, Instant snapshotAt) {
        return new CollectionEmotionCursor(deviceId, cardId, date, snapshotAt, snapshotAt, Long.MAX_VALUE);
    }

    public static CollectionEmotionCursor of(UUID deviceId, UUID cardId, LocalDate date,
            Instant snapshotAt, Instant lastCreatedAt, long lastId) {
        return new CollectionEmotionCursor(deviceId, cardId, date, snapshotAt, lastCreatedAt, lastId);
    }

    public void validateRequest(UUID requestedDeviceId, UUID requestedCardId, LocalDate cardDate, Instant now) {
        if (!deviceId.equals(requestedDeviceId) || !cardId.equals(requestedCardId) || !date.equals(cardDate)
                || snapshotAt.isAfter(now) || lastCreatedAt.isAfter(snapshotAt) || lastId < 1
                || lastCreatedAt.isBefore(cardDate.atStartOfDay(KOREA).toInstant())
                || !lastCreatedAt.isBefore(cardDate.plusDays(1).atStartOfDay(KOREA).toInstant())) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }

    public CollectionEmotionCursor next(Instant createdAt, long id) {
        return of(deviceId, cardId, date, snapshotAt, createdAt, id);
    }
}
