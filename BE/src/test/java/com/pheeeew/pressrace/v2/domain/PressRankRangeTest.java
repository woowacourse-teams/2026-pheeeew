package com.pheeeew.pressrace.v2.domain;

import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PressRankRangeTest {

    @Test
    void 구간_첫_원소는_주어진_공동_순위를_쓰고_점수가_바뀌면_전체_위치로_순위를_정한다() {
        // given: 전체 순서 9, 5, 5, 5, 3, 3 에서 인덱스 2부터 읽은 구간. 첫 원소는 앞 구간의 5 와 동점이라 2위다.
        PressRankRange range = PressRankRange.of(2, 2, List.of(
                참가자(3, 5),
                참가자(4, 5),
                참가자(5, 3),
                참가자(6, 3)
        ));

        // when
        List<PressRanker> rankers = range.rankers();

        // then
        assertThat(rankers).extracting(PressRanker::rank).containsExactly(2L, 2L, 5L, 5L);
    }
}
