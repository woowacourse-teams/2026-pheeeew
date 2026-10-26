package com.pheeeew.pressrace.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;
import java.util.UUID;

public record PendingPress(UUID requestId, UUID connectionGeneration, Map<EmotionState, Integer> counts) {

    public static PendingPress of(UUID requestId, UUID connectionGeneration, Map<EmotionState, Integer> counts) {
        return new PendingPress(requestId, connectionGeneration, counts);
    }
}
