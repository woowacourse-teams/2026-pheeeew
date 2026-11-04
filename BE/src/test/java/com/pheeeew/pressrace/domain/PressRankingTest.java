package com.pheeeew.pressrace.domain;

import static com.pheeeew.pressrace.fixture.PressRankingFixture.DATE;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.NOW;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.OBSERVER_ID;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.순위;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.점수가_내림차순인_참가자들;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PressRankingTest {

    @Test
    void 공동_일위에서_단독_일위가_되면_동점_상대들을_각각_추월한다() {
        // given
        var previous = 순위(List.of(참가자(0, 10), 참가자(1, 10), 참가자(2, 10)));
        var latest = 순위(List.of(참가자(0, 11), 참가자(1, 10), 참가자(2, 10)));

        // when
        var overtakes = latest.overtakesSince(previous, OBSERVER_ID);

        // then
        assertThat(overtakes.passed()).isEqualTo(2);
        assertThat(overtakes.passedBy()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"10, 20, 20, 20, 0, 0", "10, 20, 21, 20, 1, 0",
            "20, 10, 20, 20, 0, 0", "20, 10, 20, 21, 0, 1"})
    void 동점_도달은_제외하고_실제_초과만_양방향으로_센다(
            long beforeMe, long beforeOther, long afterMe, long afterOther, int passed, int passedBy) {
        // given
        var previous = 순위(List.of(참가자(0, beforeMe), 참가자(1, beforeOther)));
        var latest = 순위(List.of(참가자(0, afterMe), 참가자(1, afterOther)));

        // when
        var overtakes = latest.overtakesSince(previous, OBSERVER_ID);

        // then
        assertThat(overtakes).isEqualTo(PressRanking.Overtakes.of(passed, passedBy));
    }

    @Test
    void 첫_참여는_기존_상대만_넘고_기존_참가자는_새_상대에게_추월당할_수_있다() {
        // given
        var previous = 순위(List.of(참가자(1, 5)));
        var latest = 순위(List.of(참가자(0, 10), 참가자(1, 5), 참가자(2, 15)));

        // when / then
        assertThat(latest.overtakesSince(previous, OBSERVER_ID)).isEqualTo(PressRanking.Overtakes.of(1, 0));
        assertThat(latest.overtakesSince(previous, 참가자(1, 5).deviceId()))
                .isEqualTo(PressRanking.Overtakes.of(0, 2));
        assertThat(latest.overtakesSince(previous, 참가자(2, 15).deviceId()))
                .isEqualTo(PressRanking.Overtakes.of(1, 0));
    }

    @Test
    void 미참여_관찰자와_다른_날짜는_추월_알림을_받지_않는다() {
        // given
        var previous = 순위(List.of(참가자(1, 5)));
        var latest = 순위(List.of(참가자(1, 10), 참가자(2, 15)));
        var tomorrow = PressRanking.of(DATE.plusDays(1), NOW.plusSeconds(86400),
                List.of(참가자(1, 20), 참가자(2, 15)));

        // when / then
        assertThat(latest.overtakesSince(previous, OBSERVER_ID)).isEqualTo(PressRanking.Overtakes.of(0, 0));
        assertThat(tomorrow.overtakesSince(latest, 참가자(1, 10).deviceId()))
                .isEqualTo(PressRanking.Overtakes.of(0, 0));
    }

    @Test
    void 같은_등수라도_서로_다른_상대의_양방향_추월을_상쇄하지_않는다() {
        // given
        var previous = 순위(List.of(참가자(0, 10), 참가자(1, 20), 참가자(2, 5)));
        var latest = 순위(List.of(참가자(0, 25), 참가자(1, 20), 참가자(2, 30)));

        // when
        var overtakes = latest.overtakesSince(previous, OBSERVER_ID);

        // then
        assertThat(latest.forDevice(OBSERVER_ID, PressView.PRESS).me().rank())
                .isEqualTo(previous.forDevice(OBSERVER_ID, PressView.PRESS).me().rank());
        assertThat(overtakes).isEqualTo(PressRanking.Overtakes.of(1, 1));
    }

    @Test
    void 동점은_공개_UUID_unsigned_순서로_표시하고_등수는_점수만으로_계산한다() {
        // given
        var first = 참가자(1, UUID.fromString("00000000-0000-0000-0000-000000000001"), 10, NOW);
        var second = 참가자(2, UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"), 5, NOW);
        var third = 참가자(3, UUID.fromString("80000000-0000-0000-0000-000000000000"), 5, NOW);
        var last = 참가자(4, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), 1, NOW);

        // when
        PressRanking ranking = 순위(List.of(last, third, first, second));

        // then
        assertThat(ranking.forDevice(third.deviceId(), PressView.ALL).top100())
                .extracting(PressRanking.Member::publicId)
                .containsExactly(first.publicId().toString(), second.publicId().toString(),
                        third.publicId().toString(), last.publicId().toString());
        assertThat(ranking.forDevice(third.deviceId(), PressView.ALL).top100())
                .extracting(PressRanking.Member::rank).containsExactly(1L, 2L, 2L, 4L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void 미참여자는_있는_상위_최대_두명과_미참여_상태를_받는다(int count) {
        // given
        var participants = 점수가_내림차순인_참가자들(count);

        // when
        var screen = 순위(participants).forDevice(OBSERVER_ID, PressView.PRESS);

        // then
        assertThat(screen.nearby()).extracting(PressRanking.Member::publicId)
                .containsExactlyElementsOf(participants.stream().limit(2)
                        .map(participant -> participant.publicId().toString()).toList());
        assertThat(screen.me().participating()).isFalse();
        assertThat(screen.me().rank()).isNull();
        assertThat(screen.me().score()).isZero();
        assertThat(screen.me().publicId()).isNull();
        assertThat(screen.top100()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void 세명보다_적으면_참가자_자신과_있는_사람만_반환한다(int count) {
        // given
        var participants = 점수가_내림차순인_참가자들(count);
        var me = participants.getLast();

        // when
        var screen = 순위(participants).forDevice(me.deviceId(), PressView.PRESS);

        // then
        assertThat(screen.nearby()).hasSize(count).contains(screen.me());
        assertThat(screen.me().rank()).isEqualTo(count);
        assertThat(screen.me().participating()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 0", "2, 1", "3, 2", "4, 2"})
    void 내_주변_창은_앞뒤_경계에서_보정하여_세명을_제공한다(int myIndex, int start) {
        // given
        var participants = 점수가_내림차순인_참가자들(5);
        var me = participants.get(myIndex);

        // when
        var screen = 순위(participants).forDevice(me.deviceId(), PressView.PRESS);

        // then
        assertThat(screen.nearby()).extracting(PressRanking.Member::publicId)
                .containsExactlyElementsOf(participants.subList(start, start + 3).stream()
                        .map(participant -> participant.publicId().toString()).toList());
        assertThat(screen.nearby()).contains(screen.me());
    }

    @Test
    void 공동_일위가_많아도_공개_UUID_표시_위치_주변에_나를_포함한다() {
        // given
        var participants = LongStream.rangeClosed(1, 7).mapToObj(id -> 참가자(id, 10)).toList();
        var me = participants.get(4);

        // when
        var screen = 순위(participants).forDevice(me.deviceId(), PressView.PRESS);

        // then
        assertThat(screen.nearby()).extracting(PressRanking.Member::publicId)
                .containsExactlyElementsOf(participants.subList(3, 6).stream()
                        .map(participant -> participant.publicId().toString()).toList());
        assertThat(screen.nearby()).extracting(PressRanking.Member::rank).containsOnly(1L);
        assertThat(screen.nearby()).contains(screen.me());
    }

    @ParameterizedTest
    @CsvSource({"true, 100", "false, 104"})
    void 백명_경계를_고정하고_공동_백위나_백위_밖의_나와_주변도_계산한다(boolean ties, long myRank) {
        // given
        var participants = LongStream.rangeClosed(1, 105)
                .mapToObj(id -> 참가자(id, ties && id >= 100 ? 1 : 200 - id)).toList();
        var me = participants.get(103);

        // when
        var screen = 순위(participants).forDevice(me.deviceId(), PressView.ALL);

        // then
        assertThat(screen.top100()).hasSize(100).doesNotContain(screen.me());
        assertThat(screen.top100().getLast().rank()).isEqualTo(100L);
        assertThat(screen.top100().getLast().publicId()).isEqualTo(participants.get(99).publicId().toString());
        assertThat(screen.me().rank()).isEqualTo(myRank);
        assertThat(screen.nearby()).extracting(PressRanking.Member::publicId)
                .containsExactlyElementsOf(participants.subList(102, 105).stream()
                        .map(participant -> participant.publicId().toString()).toList());
    }

    @Test
    void 활동은_오초_만료_직전까지만_유효하며_입력_없이도_만료된다() {
        // given
        var participants = List.of(
                참가자(1, new UUID(0, 1), 3, NOW.minusSeconds(5).plusNanos(1)),
                참가자(2, new UUID(0, 2), 2, NOW.minusSeconds(5)),
                참가자(3, new UUID(0, 3), 1, NOW.minusSeconds(5).minusNanos(1)));

        // when
        var beforeExpiry = PressRanking.of(DATE, NOW, participants).forDevice(OBSERVER_ID, PressView.ALL);
        var afterExpiry = PressRanking.of(DATE, NOW.plusNanos(1), participants)
                .forDevice(OBSERVER_ID, PressView.ALL);

        // then
        assertThat(beforeExpiry.activeCount()).isEqualTo(1);
        assertThat(afterExpiry.activeCount()).isZero();
        assertThat(afterExpiry.top100()).isEqualTo(beforeExpiry.top100());
    }

    @Test
    void 자정의_새날_관찰은_전날_활동과_참가_상태를_이어받지_않는다() {
        // given
        Instant midnight = Instant.parse("2026-10-26T15:00:00Z");
        var participant = 참가자(1, new UUID(0, 1), 10, midnight.minusSeconds(1));
        var yesterday = PressRanking.of(DATE, midnight.minusNanos(1), List.of(participant));

        // when
        var today = PressRanking.of(DATE.plusDays(1), midnight, List.of())
                .forDevice(participant.deviceId(), PressView.ALL);

        // then
        assertThat(yesterday.forDevice(participant.deviceId(), PressView.ALL).activeCount()).isEqualTo(1);
        assertThat(today.date()).isEqualTo(DATE.plusDays(1));
        assertThat(today.activeCount()).isZero();
        assertThat(today.top100()).isEmpty();
        assertThat(today.nearby()).isEmpty();
        assertThat(today.me().participating()).isFalse();
        assertThat(today.me().rank()).isNull();
    }
}
