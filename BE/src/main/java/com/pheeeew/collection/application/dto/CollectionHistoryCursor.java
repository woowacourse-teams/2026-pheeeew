package com.pheeeew.collection.application.dto;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_CURSOR;

import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;

public record CollectionHistoryCursor(LocalDate beforeDate, EmotionState emotion, LocalDate lastDate, long lastId) {

    public static CollectionHistoryCursor initial(LocalDate beforeDate, EmotionState emotion) {
        return new CollectionHistoryCursor(beforeDate, emotion, beforeDate, Long.MAX_VALUE);
    }

    public static CollectionHistoryCursor of(LocalDate beforeDate, EmotionState emotion, LocalDate lastDate, long lastId) {
        return new CollectionHistoryCursor(beforeDate, emotion, lastDate, lastId);
    }

    public void validateRequest(LocalDate today, EmotionState requestedEmotion) {
        if (beforeDate.getYear() < 1 || beforeDate.getYear() > 9999 || beforeDate.isAfter(today)
                || lastDate.getYear() < 1 || !lastDate.isBefore(beforeDate) || lastId < 1
                || requestedEmotion != null && requestedEmotion != emotion) {
            throw new CollectionException(COLLECTION_INVALID_CURSOR);
        }
    }

    public CollectionHistoryCursor next(LocalDate date, long id) {
        return of(beforeDate, emotion, date, id);
    }
}
