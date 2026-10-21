package com.pheeeew.common.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record RankingDay(Instant startAt, Instant endAt) {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public static RankingDay of(LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(KOREA);
        return new RankingDay(start.toInstant(), start.plusDays(1).toInstant());
    }

    public LocalDate startDate() {
        return startAt.atZone(KOREA).toLocalDate();
    }

    public LocalDate endDate() {
        return endAt.atZone(KOREA).toLocalDate();
    }
}
