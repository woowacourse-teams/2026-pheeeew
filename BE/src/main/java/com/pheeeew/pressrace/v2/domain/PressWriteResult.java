package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Map;

public record PressWriteResult(PressWriteStatus status, LocalDate storeDate, Map<EmotionState, Long> counts) {
    public static PressWriteResult of(PressWriteStatus status, LocalDate storeDate, Map<EmotionState, Long> counts) {
        return new PressWriteResult(status, storeDate, counts);
    }
}
