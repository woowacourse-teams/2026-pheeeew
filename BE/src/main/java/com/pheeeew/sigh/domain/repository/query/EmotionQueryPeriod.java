package com.pheeeew.sigh.domain.repository.query;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public record EmotionQueryPeriod(Instant startAt, Instant endAt) {

    private static final int QUERY_PERIOD_DAYS = 14;

    public EmotionQueryPeriod {
        Objects.requireNonNull(startAt);
        Objects.requireNonNull(endAt);
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }
    }

    public static EmotionQueryPeriod of(Instant startAt, Instant endAt) {
        return new EmotionQueryPeriod(startAt, endAt);
    }

    public static EmotionQueryPeriod of(Instant endAt, ZoneId zone) {
        Instant startAt = endAt.atZone(zone)
                .toLocalDate()
                .minusDays(QUERY_PERIOD_DAYS - 1)
                .atStartOfDay(zone)
                .toInstant();
        return of(startAt, endAt);
    }
}
