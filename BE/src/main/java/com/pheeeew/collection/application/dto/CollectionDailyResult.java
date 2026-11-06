package com.pheeeew.collection.application.dto;

import java.time.LocalDate;

public record CollectionDailyResult(LocalDate date, CollectionCardDetailResult card,
        long pressCount, CollectionEmotionPageResult emotions) {

    public static CollectionDailyResult of(LocalDate date, CollectionCardDetailResult card,
            long pressCount, CollectionEmotionPageResult emotions) {
        return new CollectionDailyResult(date, card, pressCount, emotions);
    }
}
