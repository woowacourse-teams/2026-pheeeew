package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.Map;

public record PressDayRead(PressReadStatus status, LocalDate date, long total, Map<EmotionState, Long> counts) {
    public static PressDayRead of(PressReadStatus status, LocalDate date, long total, Map<EmotionState, Long> counts) {
        return new PressDayRead(status, date, total, counts);
    }
}
