package com.pheeeew.pressrace.v2.domain;

import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.NOW;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.기기;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PressScreenTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);

    @Test
    void 관찰_시각만_다르면_같은_화면_상태로_본다() {
        // given
        PressScreen before = PressSnapshot.of(DATE, NOW, List.of(참가자(1, 3))).findScreen(기기(1), PressView.PRESS);
        PressScreen after = PressSnapshot.of(DATE, NOW.plusSeconds(1), List.of(참가자(1, 3)))
                .findScreen(기기(1), PressView.PRESS);

        // when & then
        assertThat(after.hasSameStateAs(before)).isTrue();
    }

    @Test
    void 날짜나_활동_인원이나_점수가_다르면_다른_화면_상태다() {
        // given
        PressScreen before = PressSnapshot.of(DATE, NOW, List.of(참가자(1, 3))).findScreen(기기(1), PressView.PRESS);
        PressScreen nextDay = PressSnapshot.of(DATE.plusDays(1), NOW, List.of(참가자(1, 3)))
                .findScreen(기기(1), PressView.PRESS);
        PressScreen inactive = PressSnapshot.of(DATE, NOW.plusSeconds(5), List.of(참가자(1, 3)))
                .findScreen(기기(1), PressView.PRESS);
        PressScreen pressed = PressSnapshot.of(DATE, NOW, List.of(참가자(1, 4))).findScreen(기기(1), PressView.PRESS);

        // when & then
        assertThat(nextDay.hasSameStateAs(before)).isFalse();
        assertThat(inactive.hasSameStateAs(before)).isFalse();
        assertThat(pressed.hasSameStateAs(before)).isFalse();
        assertThat(before.hasSameStateAs(null)).isFalse();
    }
}
