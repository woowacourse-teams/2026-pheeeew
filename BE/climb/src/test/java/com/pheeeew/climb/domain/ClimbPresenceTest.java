package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClimbPresenceTest {

    @Test
    void 내_높이_위아래_범위_안의_사람만_보인다() {
        // given
        ClimbPresence presence = new ClimbPresence(List.of(at(1, 100), at(2, 69.9), at(3, 70), at(4, 130), at(5, 130.5)));

        // when
        List<ClimbPresent> visible = presence.visibleTo(1, 100, 30, 20);

        // then
        assertThat(visible).extracting(ClimbPresent::n).containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void 가까운_순서로_상한까지만_보이고_나는_빠진다() {
        // given
        List<ClimbPresent> everyone = new ArrayList<>();
        everyone.add(at(0, 300));
        for (int n = 1; n <= 25; n++) {
            everyone.add(at(n, n % 2 == 0 ? 300 + n : 300 - n));
        }
        ClimbPresence presence = new ClimbPresence(everyone);

        // when
        List<ClimbPresent> visible = presence.visibleTo(0, 300, 30, 20);

        // then
        assertThat(visible).extracting(ClimbPresent::n)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
    }

    @Test
    void 나만_있으면_아무도_보이지_않는다() {
        // given
        ClimbPresence presence = new ClimbPresence(List.of(at(1, 10)));

        // when
        List<ClimbPresent> visible = presence.visibleTo(1, 10, 30, 20);

        // then
        assertThat(visible).isEmpty();
    }

    static ClimbPresent at(int n, double h) {
        return new ClimbPresent(n, 1, new ClimbPose(640, h, 0, 0, 1, 0));
    }
}
