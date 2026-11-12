package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClimbRateLimitTest {

    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");

    @Test
    void 창_안에서는_한도까지만_허용하고_창이_지나면_다시_허용한다() {
        // given
        ClimbRateLimit limit = new ClimbRateLimit(2, Duration.ofHours(1));

        // when
        boolean first = limit.tryAcquire("1.2.3.4", T0);
        boolean second = limit.tryAcquire("1.2.3.4", T0.plusSeconds(10));
        boolean third = limit.tryAcquire("1.2.3.4", T0.plusSeconds(20));
        boolean otherKey = limit.tryAcquire("5.6.7.8", T0.plusSeconds(20));
        boolean nextWindow = limit.tryAcquire("1.2.3.4", T0.plus(Duration.ofHours(1)));

        // then
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
        assertThat(otherKey).isTrue();
        assertThat(nextWindow).isTrue();
    }
}
