package com.pheeeew.pressrace.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PressSessionResult(Map<EmotionState, Long> counts, List<PressInputResult> results, LocalDate date) {

    public static PressSessionResult of(Map<EmotionState, Long> counts, List<PressInputResult> results, LocalDate date) {
        return new PressSessionResult(counts, results, date);
    }
}
