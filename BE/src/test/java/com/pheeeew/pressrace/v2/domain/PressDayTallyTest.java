package com.pheeeew.pressrace.v2.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PressDayTallyTest {

    private static final LocalDate DATE = LocalDate.parse("2026-11-09");
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    @Test
    void 저장된_개수를_읽은_뒤_입력을_더해_누적값을_만든다() {
        // given
        var tally = PressDayTally.builder().deviceId(1L).date(DATE).build();
        tally.load(Map.of(EmotionState.ANGRY, 10L));

        // when
        tally.add(Map.of(EmotionState.ANGRY, 3, EmotionState.IRRITATED, 2), NOW);

        // then
        var snapshot = tally.snapshot();
        assertThat(snapshot.counts()).containsEntry(EmotionState.ANGRY, 13L).containsEntry(EmotionState.IRRITATED, 2L);
        assertThat(snapshot.total()).isEqualTo(15L);
        assertThat(snapshot.lastAppliedAt()).isEqualTo(NOW);
    }

    @Test
    void 반영하는_사이에_들어온_입력은_다시_반영_대상으로_남는다() {
        // given
        var tally = PressDayTally.builder().deviceId(1L).date(DATE).build();
        tally.load(Map.of());
        tally.add(Map.of(EmotionState.ANGRY, 1), NOW);
        var flushing = tally.snapshot();
        tally.add(Map.of(EmotionState.ANGRY, 1), NOW.plusSeconds(1));

        // when
        tally.markFlushed(flushing.version());

        // then
        assertThat(tally.isDirty()).isTrue();
    }

    @Test
    void 마지막_버전까지_반영하면_반영_대상에서_빠진다() {
        // given
        var tally = PressDayTally.builder().deviceId(1L).date(DATE).build();
        tally.load(Map.of());
        tally.add(Map.of(EmotionState.ANGRY, 1), NOW);
        var latest = tally.snapshot();

        // when
        tally.markFlushed(latest.version());

        // then
        assertThat(tally.isDirty()).isFalse();
    }

    @Test
    void 응답용_개수는_누르지_않은_감정도_0으로_채운다() {
        // given
        var tally = PressDayTally.builder().deviceId(1L).date(DATE).build();
        tally.load(Map.of());

        // when
        var counts = tally.counts();

        // then
        assertThat(counts).containsOnlyKeys(EmotionState.values());
        assertThat(counts.values()).containsOnly(0L);
    }
}
