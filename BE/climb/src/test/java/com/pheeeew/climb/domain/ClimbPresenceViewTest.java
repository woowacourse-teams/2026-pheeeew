package com.pheeeew.climb.domain;

import static com.pheeeew.climb.domain.ClimbPresenceTest.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClimbPresenceViewTest {

    @Test
    void 처음_보이는_사람은_들어옴과_상태를_함께_보낸다() {
        // given
        ClimbPresenceView view = new ClimbPresenceView();

        // when
        ClimbPresenceView.Delta delta = view.next(List.of(at(3, 10), at(4, 12)));

        // then
        assertThat(delta.entered()).containsExactly(3, 4);
        assertThat(delta.changed()).extracting(ClimbPresent::n).containsExactly(3, 4);
        assertThat(delta.left()).isEmpty();
    }

    @Test
    void 새_상태가_없는_사람은_빼고_새_상태가_온_사람만_보낸다() {
        // given
        ClimbPresenceView view = new ClimbPresenceView();
        view.next(List.of(at(3, 10), at(4, 12)));
        ClimbPresent moved = new ClimbPresent(4, 2, new ClimbPose(650, 12.5, 100, 0, 1, 0));

        // when
        ClimbPresenceView.Delta delta = view.next(List.of(at(3, 10), moved));
        ClimbPresenceView.Delta still = view.next(List.of(at(3, 10), moved));

        // then
        assertThat(delta.entered()).isEmpty();
        assertThat(delta.changed()).containsExactly(moved);
        assertThat(still.isEmpty()).isTrue();
    }

    @Test
    void 보이지_않게_된_사람은_나감으로_알리고_다시_보이면_들어옴부터_보낸다() {
        // given
        ClimbPresenceView view = new ClimbPresenceView();
        view.next(List.of(at(3, 10), at(4, 12)));

        // when
        ClimbPresenceView.Delta gone = view.next(List.of(at(4, 12)));
        ClimbPresenceView.Delta back = view.next(List.of(at(3, 10), at(4, 12)));

        // then
        assertThat(gone.left()).containsExactly(3);
        assertThat(gone.changed()).isEmpty();
        assertThat(back.entered()).containsExactly(3);
        assertThat(back.changed()).extracting(ClimbPresent::n).containsExactly(3);
    }
}
