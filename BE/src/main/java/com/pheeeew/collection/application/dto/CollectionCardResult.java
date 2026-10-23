package com.pheeeew.collection.application.dto;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionCardResult(UUID cardId, LocalDate date, EmotionState state, long cycle, int combinationIndex,
        String cardVersion, Instant claimedAt, CollectionCardImages images) {

    public static CollectionCardResult of(CollectionCard card, CollectionCardImages images) {
        return new CollectionCardResult(card.getPublicId(), card.getCardDate(), card.getState(), card.getCycle(),
                card.getCombinationIndex(), card.getCardVersion(), card.getClaimedAt(), images);
    }
}
