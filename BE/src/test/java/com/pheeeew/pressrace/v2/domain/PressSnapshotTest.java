package com.pheeeew.pressrace.v2.domain;

import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.NOW;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.기기;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.순위;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.참가자;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.점수가_내림차순인_참가자들;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PressSnapshotTest {

    @Test
    void 동점은_공개_ID_unsigned_순서로_표시하고_순위는_점수만으로_정한다() {
        // given: signed 비교라면 8000... 이 7fff... 보다 앞선다.
        UUID upper = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID lower = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        PressSnapshot snapshot = 순위(List.of(
                참가자(1, upper, 5, NOW),
                참가자(2, lower, 5, NOW),
                참가자(3, new UUID(0, 3), 9, NOW),
                참가자(4, new UUID(0, 4), 1, NOW)
        ));

        // when
        PressScreen screen = snapshot.findScreen(기기(4), PressView.ALL);

        // then
        assertThat(screen.top100()).extracting(PressRanker::publicId, PressRanker::rank).containsExactly(
                tuple(new UUID(0, 3).toString(), 1L),
                tuple(lower.toString(), 2L),
                tuple(upper.toString(), 2L),
                tuple(new UUID(0, 4).toString(), 4L)
        );
        assertThat(screen.me().rank()).isEqualTo(4);
    }

    @Test
    void 활동_인원은_마지막_반영_후_오초_직전까지만_센다() {
        // given
        PressSnapshot snapshot = 순위(List.of(
                참가자(1, new UUID(0, 1), 3, NOW.minus(Duration.ofSeconds(5)).plusNanos(1)),
                참가자(2, new UUID(0, 2), 3, NOW.minus(Duration.ofSeconds(5))),
                참가자(3, new UUID(0, 3), 3, NOW.minus(Duration.ofSeconds(5)).minusNanos(1)),
                참가자(4, new UUID(0, 4), 3, NOW)
        ));

        // when
        PressScreen screen = snapshot.findScreen(기기(1), PressView.PRESS);

        // then
        assertThat(screen.activeCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void 미참여자는_상위_최대_두명과_미참여_상태를_받는다(int count) {
        // given
        PressSnapshot snapshot = 순위(점수가_내림차순인_참가자들(count));

        // when
        PressScreen screen = snapshot.findScreen(기기(999), PressView.PRESS);

        // then
        assertThat(screen.me()).isEqualTo(PressRanker.absent());
        assertThat(screen.me().rank()).isNull();
        assertThat(screen.me().publicId()).isNull();
        assertThat(screen.nearby()).extracting(PressRanker::rank)
                .containsExactlyElementsOf(List.of(1L, 2L).subList(0, Math.min(2, count)));
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "2, 1", "5, 4", "10, 8"})
    void 내_주변은_나를_포함한_세명이고_앞뒤_경계에서_보정한다(long myId, long firstRank) {
        // given
        PressSnapshot snapshot = 순위(점수가_내림차순인_참가자들(10));

        // when
        PressScreen screen = snapshot.findScreen(기기(myId), PressView.PRESS);

        // then
        assertThat(screen.nearby()).extracting(PressRanker::rank)
                .containsExactly(firstRank, firstRank + 1, firstRank + 2);
        assertThat(screen.nearby()).contains(screen.me());
    }

    @Test
    void 전체_화면은_첫_백명으로_고정하고_백위_밖의_나도_실제_순위와_주변을_받는다() {
        // given
        PressSnapshot snapshot = 순위(점수가_내림차순인_참가자들(150));

        // when
        PressScreen all = snapshot.findScreen(기기(120), PressView.ALL);
        PressScreen press = snapshot.findScreen(기기(120), PressView.PRESS);

        // then
        assertThat(all.top100()).hasSize(100);
        assertThat(all.top100().getLast().rank()).isEqualTo(100);
        assertThat(all.me().rank()).isEqualTo(120);
        assertThat(all.nearby()).extracting(PressRanker::rank).containsExactly(119L, 120L, 121L);
        assertThat(press.top100()).isEmpty();
    }

    @Test
    void 공동_백위가_있어도_전체_목록을_백명보다_늘리지_않는다() {
        // given
        List<PressParticipant> participants = new ArrayList<>(LongStream.rangeClosed(1, 99)
                .mapToObj(id -> 참가자(id, 102 - id))
                .toList());
        participants.add(참가자(100, 2));
        participants.add(참가자(101, 2));
        PressSnapshot snapshot = 순위(participants);

        // when
        PressScreen screen = snapshot.findScreen(기기(101), PressView.ALL);

        // then
        assertThat(screen.top100()).hasSize(100);
        assertThat(screen.top100().getLast().rank()).isEqualTo(100);
        assertThat(screen.top100()).doesNotContain(screen.me());
        assertThat(screen.me().rank()).isEqualTo(100);
    }

    @Test
    void 닉네임이_없는_참가자는_익명으로_표시한다() {
        // given
        PressSnapshot snapshot = 순위(List.of(PressParticipant.of(기기(1), new UUID(0, 1), null, 3, NOW, null)));

        // when
        PressScreen screen = snapshot.findScreen(기기(1), PressView.PRESS);

        // then
        assertThat(screen.me().nickname()).isEqualTo("익명");
    }
}
