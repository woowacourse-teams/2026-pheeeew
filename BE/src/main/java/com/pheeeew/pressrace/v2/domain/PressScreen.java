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

    /** 관찰 시각(generatedAt)만 다른 화면은 같은 상태로 본다. */
    public boolean hasSameStateAs(PressScreen other) {
        return other != null
                && date.equals(other.date)
                && activeCount == other.activeCount
                && view == other.view
                && me.equals(other.me)
                && nearby.equals(other.nearby)
                && top100.equals(other.top100);
    }
}
