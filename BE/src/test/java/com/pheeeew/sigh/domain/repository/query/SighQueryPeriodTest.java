package com.pheeeew.sigh.domain.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SighQueryPeriodTest {

    @ParameterizedTest
    @CsvSource({
            "Asia/Seoul, 2026-09-01T15:00:00Z",
            "UTC, 2026-09-01T00:00:00Z"
    })
    void 지정한_시간대의_날짜로_오늘을_포함한_14일_조회_기간을_계산한다(String zone, String expectedStartAt) {
        // given
        Instant endAt = Instant.parse("2026-09-14T15:00:00Z");

        // when
        SighQueryPeriod period = SighQueryPeriod.of(endAt, ZoneId.of(zone));

        // then
        assertThat(period.startAt()).isEqualTo(Instant.parse(expectedStartAt));
        assertThat(period.endAt()).isEqualTo(endAt);
    }

    @Test
    void 조회_시작_시각이_종료_시각보다_늦으면_거부한다() {
        // given
        Instant endAt = Instant.parse("2026-09-14T15:00:00Z");
        Instant startAt = endAt.plusSeconds(1);

        // when / then
        assertThatThrownBy(() -> SighQueryPeriod.of(startAt, endAt))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
