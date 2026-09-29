package com.pheeeew.groups.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public record RankingWeek(Instant startAt, Instant endAt) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public static RankingWeek of(Instant now, int weeksAgo) {
        LocalDate monday = now.atZone(KOREA)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(weeksAgo);
        ZonedDateTime start = monday.atStartOfDay(KOREA);

        return new RankingWeek(start.toInstant(), start.plusWeeks(1).toInstant());
    }
}
