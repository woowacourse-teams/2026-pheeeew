package com.pheeeew.groups.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RankingWeekTest {

    private static final Instant 월요일_시작 = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant 다음_월요일_시작 = Instant.parse("2026-09-27T15:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-09-20T15:00:00Z",
            "2026-09-23T04:30:00Z",
            "2026-09-27T14:59:59Z"
    })
    void 한_주는_월요일_영시_한국시간에_시작한다(String 지금) {
        // when
        RankingWeek week = RankingWeek.of(Instant.parse(지금), 0);

        // then
        assertThat(week.startAt()).isEqualTo(월요일_시작);
        assertThat(week.endAt()).isEqualTo(다음_월요일_시작);
    }

    @Test
    void 일요일_자정_직전은_아직_같은_주다() {
        // given
        Instant 일요일_이십삼시_오십구분_한국시간 = Instant.parse("2026-09-27T14:59:59Z");

        // when
        RankingWeek week = RankingWeek.of(일요일_이십삼시_오십구분_한국시간, 0);

        // then
        assertThat(week.startAt()).isEqualTo(월요일_시작);
    }

    @Test
    void 월요일_영시가_되면_새_주로_넘어간다() {
        // given
        Instant 월요일_영시_한국시간 = Instant.parse("2026-09-27T15:00:00Z");

        // when
        RankingWeek week = RankingWeek.of(월요일_영시_한국시간, 0);

        // then
        assertThat(week.startAt()).isEqualTo(다음_월요일_시작);
    }

    @Test
    void 지난주는_직전_월요일부터_이번주_시작까지다() {
        // when
        RankingWeek week = RankingWeek.of(Instant.parse("2026-09-23T04:30:00Z"), 1);

        // then
        assertThat(week.startAt()).isEqualTo(Instant.parse("2026-09-13T15:00:00Z"));
        assertThat(week.endAt()).isEqualTo(월요일_시작);
    }

    @Test
    void 끝은_다음_주_시작과_맞닿고_겹치지_않는다() {
        // when
        RankingWeek 이번주 = RankingWeek.of(Instant.parse("2026-09-23T04:30:00Z"), 0);
        RankingWeek 지난주 = RankingWeek.of(Instant.parse("2026-09-23T04:30:00Z"), 1);

        // then
        assertThat(지난주.endAt()).isEqualTo(이번주.startAt());
    }
}
