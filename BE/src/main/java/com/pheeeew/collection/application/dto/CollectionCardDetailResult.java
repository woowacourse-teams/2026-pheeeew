package com.pheeeew.collection.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionCardDetailResult(UUID cardId, LocalDate date, EmotionState state,
        long stampCount, long pressCount, CollectionCardImages images) {

    public static CollectionCardDetailResult of(UUID cardId, LocalDate date, EmotionState state,
            long stampCount, long pressCount, CollectionCardImages images) {
        return new CollectionCardDetailResult(cardId, date, state, stampCount, pressCount, images);
    }
}
