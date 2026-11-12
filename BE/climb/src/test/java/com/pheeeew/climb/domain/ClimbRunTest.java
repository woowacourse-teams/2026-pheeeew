package com.pheeeew.climb.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClimbRunTest {

    private static final ClimbMap MAP = ClimbMap.of("v1", 662, List.of(135, 261, 397, 523, 653), 300);
    private static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");

    @Test
    void 범위_안의_높이_보고는_최고_높이와_도달_시각을_갱신한다() {
        // given
        ClimbRun run = started();

        // when
        boolean accepted = run.pos(12, 15, 1, 3, 40, T0.plusSeconds(3));

        // then
        assertThat(accepted).isTrue();
        ClimbRunRecord record = run.record();
        assertThat(record.best()).isEqualTo(15);
        assertThat(record.bestAt()).isEqualTo(T0.plusSeconds(3));
        assertThat(record.height()).isEqualTo(12);
        assertThat(record.x()).isEqualTo(40);
        assertThat(record.falls()).isEqualTo(1);
        assertThat(record.playSeconds()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"-1, 5", "663, 663", "10, 9", "5, 663"})
    void 범위를_벗어나거나_최고_높이가_현재_높이보다_낮은_보고는_버린다(int h, int best) {
        // given
        ClimbRun run = started();

        // when
        boolean accepted = run.pos(h, best, 0, 600, 0, T0.plusSeconds(600));

        // then
        assertThat(accepted).isFalse();
        assertThat(run.record().best()).isZero();
    }

    @Test
    void 이초_동안_스무_칸까지는_받고_넘으면_버린다() {
        // given
        ClimbRun run = started();

        // when
        boolean twenty = run.pos(20, 20, 0, 1, 0, T0.plusSeconds(1));
        boolean twentyOne = run.pos(21, 21, 0, 1, 0, T0.plusMillis(1500));

        // then
        assertThat(twenty).isTrue();
        assertThat(twentyOne).isFalse();
        assertThat(run.record().best()).isEqualTo(20);
    }

    @Test
    void 짧은_간격으로_여러_번_보고해도_이초_동안_오른_높이를_합쳐_검사한다() {
        // given
        ClimbRun run = started();

        // when
        boolean first = run.pos(15, 15, 0, 0, 0, T0.plusMillis(500));
        boolean second = run.pos(30, 30, 0, 1, 0, T0.plusMillis(1000));

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void 시간이_지나면_그만큼_더_높이_오를_수_있다() {
        // when
        ClimbRun run = started();

        // then
        assertThat(run.pos(40, 40, 0, 4, 0, T0.plusSeconds(4))).isTrue();
    }

    @Test
    void 구간은_순서대로만_넘을_수_있다() {
        // given
        ClimbRun run = started();

        // when
        boolean skipped = run.zone(2, 60, T0.plusSeconds(60));
        boolean first = run.zone(1, 60, T0.plusSeconds(60));
        boolean second = run.zone(2, 120, T0.plusSeconds(120));
        boolean again = run.zone(2, 121, T0.plusSeconds(121));

        // then
        assertThat(skipped).isFalse();
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(again).isFalse();
    }

    @Test
    void 구간을_다_넘지_않은_완주는_버린다() {
        // given
        ClimbRun run = started();
        passZones(run, 4);

        // when
        boolean accepted = run.finish(900, 3, T0.plusSeconds(900));

        // then
        assertThat(accepted).isFalse();
        assertThat(run.record().finishSeconds()).isNull();
    }

    @Test
    void 구간을_다_넘은_완주는_꼭대기_높이와_걸린_시간으로_기록한다() {
        // given
        ClimbRun run = started();
        passZones(run, 5);

        // when
        boolean accepted = run.finish(900, 3, T0.plusSeconds(900));

        // then
        assertThat(accepted).isTrue();
        ClimbRunRecord record = run.record();
        assertThat(record.finishSeconds()).isEqualTo(900);
        assertThat(record.finishedAt()).isEqualTo(T0.plusSeconds(900));
        assertThat(record.best()).isEqualTo(662);
        assertThat(record.suspect()).isFalse();
        assertThat(run.entry("a1b2", "피유").finished()).isTrue();
    }

    @Test
    void 서버가_잰_연결_시간보다_크게_짧은_완주_시간은_버린다() {
        // given
        ClimbRun run = started();
        passZones(run, 5);

        // when
        boolean accepted = run.finish(400, 3, T0.plusSeconds(900));

        // then
        assertThat(accepted).isFalse();
    }

    @Test
    void 완주_최소_시간보다_빠른_완주는_의심_기록으로_표시한다() {
        // given
        ClimbRun run = started();
        passZones(run, 5);

        // when
        boolean accepted = run.finish(200, 0, T0.plusSeconds(200));

        // then
        assertThat(accepted).isTrue();
        assertThat(run.record().suspect()).isTrue();
    }

    @Test
    void 완주한_등반은_더_이상_보고를_받지_않는다() {
        // given
        ClimbRun run = started();
        passZones(run, 5);
        run.finish(900, 3, T0.plusSeconds(900));

        // when
        boolean accepted = run.pos(10, 662, 0, 910, 0, T0.plusSeconds(910));

        // then
        assertThat(accepted).isFalse();
        assertThat(run.record().height()).isEqualTo(662);
    }

    @Test
    void 저장된_등반을_이어하면_최고_높이까지의_구간을_넘은_것으로_본다() {
        // given
        ClimbRunRecord saved = saved(300, 280, 600);
        ClimbRun resumed = ClimbRun.restore(saved, MAP);
        resumed.attach(T0.plusSeconds(3600));

        // when
        boolean third = resumed.zone(3, 700, T0.plusSeconds(3700));

        // then
        assertThat(third).isTrue();
    }

    @Test
    void 이어한_등반의_높이_검사는_다시_연결한_시각부터_잰다() {
        // given
        ClimbRunRecord saved = saved(300, 280, 600);
        ClimbRun resumed = ClimbRun.restore(saved, MAP);
        resumed.attach(T0.plusSeconds(3600));

        // when
        boolean accepted = resumed.pos(318, 318, 0, 601, 0, T0.plusSeconds(3601));
        boolean jumped = resumed.pos(400, 400, 0, 602, 0, T0.plusSeconds(3602));

        // then
        assertThat(accepted).isTrue();
        assertThat(jumped).isFalse();
    }

    private static ClimbRunRecord saved(int best, int height, int playSeconds) {
        return ClimbRunRecord.of(UUID.randomUUID(), UUID.randomUUID(), "v1", "angry", best, T0, height, 0, 0,
                playSeconds, null, null, false, T0, T0);
    }

    private static ClimbRun started() {
        ClimbRun run = ClimbRun.start(UUID.randomUUID(), UUID.randomUUID(), MAP, "angry", T0);
        run.attach(T0);
        return run;
    }

    private static void passZones(ClimbRun run, int count) {
        for (int zone = 1; zone <= count; zone++) {
            run.zone(zone, zone * 100, T0.plusSeconds(zone * 100L));
        }
    }
}
