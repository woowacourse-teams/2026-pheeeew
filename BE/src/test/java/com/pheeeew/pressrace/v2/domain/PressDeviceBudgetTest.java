package com.pheeeew.pressrace.v2.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PressDeviceBudgetTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Test
    void 남은_토큰보다_많이_누르면_차감하지_않고_거절한다() {
        // given
        var budget = PressDeviceBudget.builder().deviceId(1L).refilledAt(NOW).build();
        budget.consume(100, NOW);

        // when
        var consumed = budget.consume(1, NOW);

        // then
        assertThat(consumed).isFalse();
    }

    @Test
    void 시간이_지나면_초당_20개씩_다시_채워진다() {
        // given
        var budget = PressDeviceBudget.builder().deviceId(1L).refilledAt(NOW).build();
        budget.consume(100, NOW);

        // when
        var consumed = budget.consume(20, NOW.plusSeconds(1));

        // then
        assertThat(consumed).isTrue();
        assertThat(budget.consume(1, NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void 오래_쉬어도_용량_100을_넘겨_쌓이지_않는다() {
        // given
        var budget = PressDeviceBudget.builder().deviceId(1L).refilledAt(NOW).build();

        // when
        var consumed = budget.consume(101, NOW.plusSeconds(60));

        // then
        assertThat(consumed).isFalse();
    }
}
