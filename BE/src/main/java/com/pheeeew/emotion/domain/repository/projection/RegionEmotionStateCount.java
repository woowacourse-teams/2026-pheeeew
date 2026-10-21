package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

public record RegionEmotionStateCount(String code, EmotionState state, long amount, Instant nextActivation) {

    public static RegionEmotionStateCount of(String code, EmotionState state, long amount, Instant nextActivation) {
        return new RegionEmotionStateCount(code, state, amount, nextActivation);
    }
}
