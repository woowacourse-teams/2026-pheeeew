package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;

/** day는 작성 시각의 KST 날짜다. */
public record RegionEmotionStateCount(String code, LocalDate day, EmotionState state, long amount, Instant nextActivation) {

    public static RegionEmotionStateCount of(String code, LocalDate day, EmotionState state, long amount, Instant nextActivation) {
        return new RegionEmotionStateCount(code, day, state, amount, nextActivation);
    }
}
