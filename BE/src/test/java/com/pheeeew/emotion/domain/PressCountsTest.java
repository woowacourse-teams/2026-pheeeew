package com.pheeeew.emotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PressCountsTest {

    @Test
    void 입력_순서가_반대여도_이름_오름차순으로_순회한다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(EmotionState.EXHAUSTED, EmotionState.ANGRY);

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses().keySet())
                .containsExactly(EmotionState.ANGRY, EmotionState.EXHAUSTED);
    }

    @Test
    void 다섯_감정을_뒤섞어_넣으면_선언_순서가_아니라_이름_오름차순으로_순회한다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                EmotionState.IRRITATED,
                EmotionState.DISCOURAGED,
                EmotionState.FRUSTRATED,
                EmotionState.ANGRY,
                EmotionState.EXHAUSTED
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses().keySet()).containsExactly(
                EmotionState.ANGRY,
                EmotionState.DISCOURAGED,
                EmotionState.EXHAUSTED,
                EmotionState.FRUSTRATED,
                EmotionState.IRRITATED
        );
    }

    @Test
    void 감정별_상한을_넘기면_서른까지만_적용하고_넘친_만큼_센다() {
        // given
        Map<EmotionState, Integer> 요청 = Map.of(EmotionState.ANGRY, 45);

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses()).containsExactly(Map.entry(EmotionState.ANGRY, 30));
        assertThat(묶음.perStateDropped()).isEqualTo(15);
        assertThat(묶음.totalDropped()).isZero();
    }

    @Test
    void 감정별로_정확히_서른이면_하나도_자르지_않는다() {
        // given
        Map<EmotionState, Integer> 요청 = Map.of(EmotionState.ANGRY, 30);

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses()).containsExactly(Map.entry(EmotionState.ANGRY, 30));
        assertThat(묶음.perStateDropped()).isZero();
        assertThat(묶음.totalDropped()).isZero();
    }

    @Test
    void 합이_정확히_백이면_하나도_자르지_않는다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                Map.entry(EmotionState.ANGRY, 30),
                Map.entry(EmotionState.DISCOURAGED, 30),
                Map.entry(EmotionState.EXHAUSTED, 30),
                Map.entry(EmotionState.FRUSTRATED, 10)
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.appliedTotal()).isEqualTo(100);
        assertThat(묶음.perStateDropped()).isZero();
        assertThat(묶음.totalDropped()).isZero();
    }

    @Test
    void 전체_합_상한을_넘기면_이름이_뒤인_감정부터_깎이고_영이_되면_키에서_빠진다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                Map.entry(EmotionState.FRUSTRATED, 30),
                Map.entry(EmotionState.IRRITATED, 30),
                Map.entry(EmotionState.EXHAUSTED, 30),
                Map.entry(EmotionState.DISCOURAGED, 30),
                Map.entry(EmotionState.ANGRY, 30)
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses()).containsExactly(
                Map.entry(EmotionState.ANGRY, 30),
                Map.entry(EmotionState.DISCOURAGED, 30),
                Map.entry(EmotionState.EXHAUSTED, 30),
                Map.entry(EmotionState.FRUSTRATED, 10)
        );
        assertThat(묶음.presses()).doesNotContainKey(EmotionState.IRRITATED);
        assertThat(묶음.appliedTotal()).isEqualTo(100);
        assertThat(묶음.perStateDropped()).isZero();
        assertThat(묶음.totalDropped()).isEqualTo(50);
    }

    @Test
    void 감정별_상한과_전체_합_상한을_함께_넘기면_각각_따로_센다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                Map.entry(EmotionState.ANGRY, 45),
                Map.entry(EmotionState.DISCOURAGED, 45),
                Map.entry(EmotionState.EXHAUSTED, 45),
                Map.entry(EmotionState.FRUSTRATED, 45)
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.appliedTotal()).isEqualTo(100);
        assertThat(묶음.perStateDropped()).isEqualTo(60);
        assertThat(묶음.totalDropped()).isEqualTo(20);
    }

    @Test
    void 값이_영인_감정은_결과에서_빠지고_자른_것으로도_세지_않는다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                Map.entry(EmotionState.ANGRY, 0),
                Map.entry(EmotionState.EXHAUSTED, 3)
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses()).containsExactly(Map.entry(EmotionState.EXHAUSTED, 3));
        assertThat(묶음.perStateDropped()).isZero();
        assertThat(묶음.totalDropped()).isZero();
    }

    @Test
    void 모든_값이_영이면_적용할_것이_없다() {
        // given
        Map<EmotionState, Integer> 요청 = 순서대로(
                Map.entry(EmotionState.ANGRY, 0),
                Map.entry(EmotionState.EXHAUSTED, 0)
        );

        // when
        PressCounts 묶음 = PressCounts.from(요청);

        // then
        assertThat(묶음.presses()).isEmpty();
        assertThat(묶음.appliedTotal()).isZero();
    }

    @Test
    void 빈_맵이면_적용할_것이_없다() {
        // when
        PressCounts 묶음 = PressCounts.from(Map.of());

        // then
        assertThat(묶음.presses()).isEmpty();
        assertThat(묶음.appliedTotal()).isZero();
    }

    @Test
    void 결과_맵은_바꿀_수_없다() {
        // given
        PressCounts 묶음 = PressCounts.from(Map.of(EmotionState.ANGRY, 1));

        // when
        Map<EmotionState, Integer> 적용된_것 = 묶음.presses();

        // then
        assertThatThrownBy(() -> 적용된_것.put(EmotionState.IRRITATED, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Map<EmotionState, Integer> 순서대로(EmotionState... 감정들) {
        Map<EmotionState, Integer> 요청 = new LinkedHashMap<>();
        for (EmotionState 감정 : 감정들) {
            요청.put(감정, 1);
        }

        return 요청;
    }

    @SafeVarargs
    private Map<EmotionState, Integer> 순서대로(Map.Entry<EmotionState, Integer>... 항목들) {
        Map<EmotionState, Integer> 요청 = new LinkedHashMap<>();
        for (Map.Entry<EmotionState, Integer> 항목 : 항목들) {
            요청.put(항목.getKey(), 항목.getValue());
        }

        return 요청;
    }
}
