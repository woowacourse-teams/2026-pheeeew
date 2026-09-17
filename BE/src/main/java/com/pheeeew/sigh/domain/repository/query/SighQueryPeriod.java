package com.pheeeew.sigh.domain.repository.query;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public record SighQueryPeriod(Instant startAt, Instant endAt) {

    private static final int QUERY_PERIOD_DAYS = 14;

    public SighQueryPeriod {
        Objects.requireNonNull(startAt);
        Objects.requireNonNull(endAt);
        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }
    }

    public static SighQueryPeriod of(Instant startAt, Instant endAt) {
        return new SighQueryPeriod(startAt, endAt);
    }

    public static SighQueryPeriod of(Instant endAt, ZoneId zone) {
        Instant startAt = endAt.atZone(zone)
                .toLocalDate()
                .minusDays(QUERY_PERIOD_DAYS - 1)
                .atStartOfDay(zone)
                .toInstant();
        return of(startAt, endAt);
    }
}
