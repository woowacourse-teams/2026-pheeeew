package com.pheeeew.pressrace.v2.domain;

import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.DATE;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.NOW;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.변경_없음;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.전체_읽기;
import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PressTodayObservationTest {

    @Test
    void 같은_날짜에_더_이른_Redis_시각을_읽어도_관찰_시각을_줄이지_않는다() {
        // given
        PressTodayObservation previous = PressTodayObservation.advance(null, 전체_읽기(DATE, NOW, List.of()));

        // when
        PressTodayObservation read = PressTodayObservation.advance(previous,
                전체_읽기(DATE, NOW.minusMillis(300), List.of(참가자(Map.of(EmotionState.ANGRY, 1L)))));
        PressTodayObservation unchanged = PressTodayObservation.advance(previous, 변경_없음(DATE, NOW.minusMillis(300)));

        // then
        assertThat(read.observedAt()).isEqualTo(NOW);
        assertThat(read.participants()).hasSize(1);
        assertThat(unchanged.observedAt()).isEqualTo(NOW);
    }

    @Test
    void 변경_없음이면_참가자를_유지하고_관찰_시각만_새_Redis_시각으로_나아간다() {
        // given
        PressTodayObservation previous = PressTodayObservation.advance(null,
                전체_읽기(DATE, NOW, List.of(참가자(Map.of(EmotionState.ANGRY, 1L)))));

        // when
        PressTodayObservation unchanged = PressTodayObservation.advance(previous, 변경_없음(DATE, NOW.plusMillis(250)));

        // then
        assertThat(unchanged.observedAt()).isEqualTo(NOW.plusMillis(250));
        assertThat(unchanged.participants()).isEqualTo(previous.participants());
    }

    @Test
    void 직전_관찰보다_이른_날짜는_거절한다() {
        // given
        PressTodayObservation previous = PressTodayObservation.advance(null, 전체_읽기(DATE, NOW, List.of()));

        // when, then
        assertThatThrownBy(() -> PressTodayObservation.advance(previous,
                전체_읽기(DATE.minusDays(1), NOW.plusSeconds(1), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
