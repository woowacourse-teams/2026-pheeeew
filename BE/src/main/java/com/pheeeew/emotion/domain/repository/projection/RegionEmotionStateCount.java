package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;

/** day는 작성 시각의 KST 날짜다. audio는 녹음 기록의 개수인지 여부다. */
public record RegionEmotionStateCount(String code, LocalDate day, EmotionState state, boolean audio, long amount,
                                      Instant nextActivation) {

    public static RegionEmotionStateCount of(String code, LocalDate day, EmotionState state, boolean audio, long amount,
            Instant nextActivation) {
        return new RegionEmotionStateCount(code, day, state, audio, amount, nextActivation);
    }
}
