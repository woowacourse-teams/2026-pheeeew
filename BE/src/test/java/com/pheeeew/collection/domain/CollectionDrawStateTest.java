package com.pheeeew.collection.domain;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_추첨_상태_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CollectionDrawStateTest {

    @Test
    void 한_회차의_256조합을_중복_없이_모두_추첨한다() {
        // given
        CollectionDrawState state = 기본_추첨_상태_빌더().build();
        var used = new HashSet<Integer>();
        var random = new Random(839);

        // when
        for (int count = 0; count < 256; count++) {
            var draw = state.draw(used, random);
            assertThat(draw.cycle()).isEqualTo(1);
            assertThat(used.add(draw.combinationIndex())).isTrue();
        }

        // then
        assertThat(used).hasSize(256).contains(0, 255);
        assertThat(state.getCycle()).isEqualTo(1);
    }

    @Test
    void 소진_후_새_회차의_첫_추첨만_직전_조합을_제외한다() {
        // given
        CollectionDrawState state = 기본_추첨_상태_빌더().build();
        var used = new HashSet<Integer>();
        var random = new Random(840);
        for (int count = 0; count < 256; count++) {
            used.add(state.draw(used, random).combinationIndex());
        }
        int previous = state.getLastCombinationIndex();

        // when
        var first = state.draw(used, random);
        var remaining = new HashSet<Integer>();
        remaining.add(first.combinationIndex());
        for (int count = 1; count < 256; count++) {
            var draw = state.draw(remaining, random);
            assertThat(draw.cycle()).isEqualTo(2);
            assertThat(remaining.add(draw.combinationIndex())).isTrue();
        }

        // then
        assertThat(first.cycle()).isEqualTo(2);
        assertThat(first.combinationIndex()).isNotEqualTo(previous);
        assertThat(remaining).hasSize(256).contains(previous);
        assertThat(used).hasSize(256);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256})
    void 범위를_벗어난_사용_조합을_받으면_추첨하지_않는다(int invalidIndex) {
        // given
        CollectionDrawState state = 기본_추첨_상태_빌더().build();

        // when / then
        assertThatThrownBy(() -> state.draw(Set.of(invalidIndex), new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(state.getCycle()).isEqualTo(1);
        assertThat(state.getLastCombinationIndex()).isNull();
    }

    @Test
    void 추첨_실패는_회차와_직전_조합을_변경하지_않는다() {
        // given
        CollectionDrawState state = 기본_추첨_상태_빌더().build();
        var used = new HashSet<Integer>();
        var random = new Random(1);
        for (int count = 0; count < 256; count++) {
            used.add(state.draw(used, random).combinationIndex());
        }
        int previous = state.getLastCombinationIndex();
        var failingRandom = new Random() {
            @Override
            public int nextInt(int bound) {
                throw new IllegalStateException("난수 생성 실패");
            }
        };

        // when / then
        assertThatThrownBy(() -> state.draw(used, failingRandom)).isInstanceOf(IllegalStateException.class);
        assertThat(state.getCycle()).isEqualTo(1);
        assertThat(state.getLastCombinationIndex()).isEqualTo(previous);
    }

    @Test
    void 남은_후보마다_균등_난수의_한_선택지를_대응시킨다() {
        // given
        var used = IntStream.range(0, 256).filter(index -> index != 4 && index != 19 && index != 240)
                .boxed().collect(Collectors.toSet());
        var selected = new HashSet<Integer>();

        // when
        for (int rank = 0; rank < 3; rank++) {
            int selectedRank = rank;
            var random = new Random() {
                @Override
                public int nextInt(int bound) {
                    assertThat(bound).isEqualTo(3);
                    return selectedRank;
                }
            };
            selected.add(기본_추첨_상태_빌더().build().draw(used, random).combinationIndex());
        }

        // then
        assertThat(selected).containsExactlyInAnyOrder(4, 19, 240);
        assertThat(used).hasSize(253);
    }
}
