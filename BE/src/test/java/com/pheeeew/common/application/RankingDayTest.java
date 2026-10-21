package com.pheeeew.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RankingDayTest {

    private static final LocalDate 날짜 = LocalDate.of(2026, 10, 21);

    @Test
    void 하루는_영시_한국시간에_시작하고_다음_날_영시에_끝난다() {
        // when
        RankingDay day = RankingDay.of(날짜);

        // then
        assertThat(day.startAt()).isEqualTo(Instant.parse("2026-10-20T15:00:00Z"));
        assertThat(day.endAt()).isEqualTo(Instant.parse("2026-10-21T15:00:00Z"));
    }

    @Test
    void 조회_구간은_요청한_날짜부터_다음_날까지다() {
        // when
        RankingDay day = RankingDay.of(날짜);

        // then
        assertThat(day.startDate()).isEqualTo(날짜);
        assertThat(day.endDate()).isEqualTo(날짜.plusDays(1));
    }

    @Test
    void 끝은_다음_날_시작과_맞닿고_겹치지_않는다() {
        // when
        RankingDay 오늘 = RankingDay.of(날짜);
        RankingDay 내일 = RankingDay.of(날짜.plusDays(1));

        // then
        assertThat(오늘.endAt()).isEqualTo(내일.startAt());
    }
}
