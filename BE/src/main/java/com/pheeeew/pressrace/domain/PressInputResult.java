package com.pheeeew.pressrace.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record PressInputResult(
        UUID requestId,
        String status,
        String code,
        LocalDate appliedDate,
        Map<EmotionState, Integer> appliedCounts
) {

    public static PressInputResult of(UUID requestId, String status, String code,
            LocalDate appliedDate, Map<EmotionState, Integer> appliedCounts) {
        return new PressInputResult(requestId, status, code, appliedDate, appliedCounts);
    }
}
