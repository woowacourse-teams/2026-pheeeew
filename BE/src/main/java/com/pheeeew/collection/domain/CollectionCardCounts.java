package com.pheeeew.collection.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.EnumMap;
import java.util.Map;

public record CollectionCardCounts(Map<EmotionState, Long> byEmotion) {

    public CollectionCardCounts {
        var counts = new EnumMap<EmotionState, Long>(EmotionState.class);
        for (var state : EmotionState.values()) {
            counts.put(state, byEmotion.getOrDefault(state, 0L));
        }
        byEmotion = Map.copyOf(counts);
    }

    public static CollectionCardCounts of(Map<EmotionState, Long> byEmotion) {
        return new CollectionCardCounts(byEmotion);
    }

    public long total() {
        return byEmotion.values().stream().mapToLong(Long::longValue).sum();
    }
}
