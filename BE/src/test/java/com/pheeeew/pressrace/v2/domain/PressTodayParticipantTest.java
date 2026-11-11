package com.pheeeew.pressrace.v2.domain;

import static com.pheeeew.pressrace.v2.fixture.PressTodayFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.domain.EmotionState;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PressTodayParticipantTest {

    @ParameterizedTest
    @CsvSource({
            "ANGRY, FRUSTRATED, ANGRY",
            "FRUSTRATED, IRRITATED, FRUSTRATED",
            "DISCOURAGED, IRRITATED, DISCOURAGED"
    })
    void 대표_감정이_공동_최다면_선언_순서가_아니라_이름_오름차순으로_고른다(EmotionState first, EmotionState second,
            EmotionState expected) {
        // given
        PressTodayParticipant participant = 참가자(Map.of(first, 3L, second, 3L, EmotionState.EXHAUSTED, 1L));

        // when
        EmotionState representative = participant.representativeState();

        // then
        assertThat(representative).isEqualTo(expected);
    }

    @Test
    void 대표_감정은_가장_많이_누른_감정이다() {
        // given
        PressTodayParticipant participant = 참가자(Map.of(EmotionState.ANGRY, 2L, EmotionState.IRRITATED, 5L));

        // when
        EmotionState representative = participant.representativeState();

        // then
        assertThat(representative).isEqualTo(EmotionState.IRRITATED);
    }

    @Test
    void 모든_감정이_0이면_대표_감정이_없다() {
        // given
        PressTodayParticipant participant = 참가자(Map.of());

        // when
        EmotionState representative = participant.representativeState();

        // then
        assertThat(representative).isNull();
    }
}
