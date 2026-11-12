package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClimbRankingTest {

    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");

    @Test
    void 최고_높이가_높은_손님이_위에_온다() {
        // given
        ClimbRankEntry low = entry(guest(1), 30, T0, null);
        ClimbRankEntry high = entry(guest(2), 80, T0.plusSeconds(60), null);

        // when
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(low, high));

        // then
        assertThat(sorted).containsExactly(high, low);
    }

    @Test
    void 최고_높이가_같으면_먼저_도달한_손님이_위에_오고_시각도_같으면_손님_ID_순이다() {
        // given
        ClimbRankEntry late = entry(guest(1), 50, T0.plusSeconds(10), null);
        ClimbRankEntry earlySecond = entry(guest(3), 50, T0, null);
        ClimbRankEntry earlyFirst = entry(guest(2), 50, T0, null);

        // when
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(late, earlySecond, earlyFirst));

        // then
        assertThat(sorted).containsExactly(earlyFirst, earlySecond, late);
    }

    @Test
    void 완주자는_걸린_시간이_짧은_순으로_완주하지_않은_손님보다_위에_온다() {
        // given
        ClimbRankEntry climbing = entry(guest(1), 661, T0, null);
        ClimbRankEntry slow = entry(guest(2), 662, T0, 900);
        ClimbRankEntry fast = entry(guest(3), 662, T0.plusSeconds(100), 600);

        // when
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(climbing, slow, fast));

        // then
        assertThat(sorted).containsExactly(fast, slow, climbing);
    }

    @Test
    void 한_손님의_여러_등반_중_가장_좋은_기록_하나만_순위에_남는다() {
        // given
        UUID guest = guest(1);
        ClimbRankEntry old = entry(guest, 120, T0, null);
        ClimbRankEntry restarted = entry(guest, 40, T0.plusSeconds(600), null);
        ClimbRankEntry other = entry(guest(2), 60, T0, null);

        // when
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(restarted, other, old));

        // then
        assertThat(sorted).containsExactly(old, other);
    }

    @Test
    void 내_순위와_바로_위아래_한_명씩을_상위_목록과_함께_만든다() {
        // given
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(
                entry(guest(1), 100, T0, null), entry(guest(2), 90, T0, null), entry(guest(3), 80, T0, null),
                entry(guest(4), 70, T0, null), entry(guest(5), 60, T0, null)));

        // when
        ClimbRankView view = ClimbRanking.view(sorted, guest(4), 2);

        // then
        assertThat(view.top()).extracting(ClimbRankView.Ranked::rank).containsExactly(1, 2);
        assertThat(view.top()).extracting(ranked -> ranked.entry().best()).containsExactly(100, 90);
        assertThat(view.me().rank()).isEqualTo(4);
        assertThat(view.me().entry().best()).isEqualTo(70);
        assertThat(view.near()).extracting(ClimbRankView.Ranked::rank).containsExactly(3, 4, 5);
    }

    @Test
    void 일등의_위와_꼴찌의_아래는_비워_둔다() {
        // given
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(
                entry(guest(1), 100, T0, null), entry(guest(2), 90, T0, null)));

        // when
        ClimbRankView first = ClimbRanking.view(sorted, guest(1), 10);
        ClimbRankView last = ClimbRanking.view(sorted, guest(2), 10);

        // then
        assertThat(first.near()).extracting(ClimbRankView.Ranked::rank).containsExactly(1, 2);
        assertThat(last.near()).extracting(ClimbRankView.Ranked::rank).containsExactly(1, 2);
    }

    @Test
    void 순위표에_없는_손님은_내_순위가_비어_있다() {
        // given
        List<ClimbRankEntry> sorted = ClimbRanking.sort(List.of(entry(guest(1), 100, T0, null)));

        // when
        ClimbRankView view = ClimbRanking.view(sorted, guest(9), 10);

        // then
        assertThat(view.top()).hasSize(1);
        assertThat(view.me()).isNull();
        assertThat(view.near()).isEmpty();
    }

    private static UUID guest(long number) {
        return new UUID(0, number);
    }

    private static ClimbRankEntry entry(UUID guestId, int best, Instant bestAt, Integer finishSeconds) {
        return ClimbRankEntry.of(UUID.randomUUID(), guestId, "a1b2", "히유", "angry", best, bestAt, finishSeconds);
    }
}
