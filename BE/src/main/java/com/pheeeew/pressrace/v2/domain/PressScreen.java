package com.pheeeew.pressrace.v2.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PressScreen(LocalDate date, Instant generatedAt, int activeCount, PressView view, PressRanker me,
                          List<PressRanker> nearby, List<PressRanker> top100) {

    public static PressScreen of(LocalDate date, Instant generatedAt, int activeCount, PressView view,
            PressRanker me, List<PressRanker> nearby, List<PressRanker> top100) {
        return new PressScreen(date, generatedAt, activeCount, view, me, nearby, top100);
    }
}
