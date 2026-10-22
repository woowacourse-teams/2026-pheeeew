package com.pheeeew.collection.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public record CollectionActivityCounts(EmotionState state, long stampCount, long pressCount) {

    public static CollectionActivityCounts of(EmotionState state, long stampCount, long pressCount) {
        return new CollectionActivityCounts(state, stampCount, pressCount);
    }
}
