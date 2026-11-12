package com.pheeeew.emotion.application.mapcache;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PinCandidatesTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);
    private static final EmotionMapBlocks NO_BLOCKS = EmotionMapBlocks.of(Set.of(), Set.of());
    private static final Long REQUESTER = 99L;

    @Test
    void 내용_필터는_메모와_녹음_핀을_구분해_선택한다() {
        // given
        var ordered = PinCandidates.of(List.of(
                EmotionMapPin.of(1L, 1L, NOW, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(2L, 1L, NOW, 127, 38, EmotionState.ANGRY, 0, null, true, "11010530"),
                EmotionMapPin.of(3L, 1L, NOW, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530")), null).ordered();

        // when
        var memo = ordered.select(cursor(EmotionContentFilter.MEMO, null), REQUESTER, NO_BLOCKS, 201);
        var audio = ordered.select(cursor(EmotionContentFilter.AUDIO, null), REQUESTER, NO_BLOCKS, 201);
        var all = ordered.select(cursor(EmotionContentFilter.ALL, null), REQUESTER, NO_BLOCKS, 201);

        // then
        assertThat(memo.pins()).extracting(EmotionMapPin::id).containsExactly(3L, 1L);
        assertThat(audio.pins()).extracting(EmotionMapPin::id).containsExactly(2L);
        assertThat(all.pins()).extracting(EmotionMapPin::id).containsExactly(3L, 2L, 1L);
    }

    @Test
    void 내_감정_범위는_요청_기기의_핀만_최신순으로_검사하고_변경도_반영한다() {
        // given
        List<EmotionMapPin> pins = new ArrayList<>();
        for (int id = 1; id <= 300; id++) {
            pins.add(pin(id, 7L, NOW));
        }
        pins.add(pin(1001, REQUESTER, NOW.minusSeconds(2)));
        pins.add(pin(1002, REQUESTER, NOW.minusSeconds(1)));
        pins.add(pin(1003, null, NOW));
        var original = PinCandidates.of(pins, null);
        var mine = cursor(EmotionContentFilter.ALL, EmotionSearchScope.MY);

        // when
        var selected = original.ordered().select(mine, REQUESTER, NO_BLOCKS, 201);
        var nothing = original.ordered().select(mine, 8L, NO_BLOCKS, 201);
        var removed = original.applyChange(contribution(1002, REQUESTER, NOW.minusSeconds(1)),
                deletedContribution(1002, REQUESTER, NOW.minusSeconds(1)), NOW, 100_000);

        // then
        assertThat(selected.pins()).extracting(EmotionMapPin::id).containsExactly(1002L, 1001L);
        assertThat(selected.scanned()).isEqualTo(2);
        assertThat(nothing.pins()).isEmpty();
        assertThat(nothing.scanned()).isZero();
        assertThat(removed.ordered().select(mine, REQUESTER, NO_BLOCKS, 201).pins())
                .extracting(EmotionMapPin::id).containsExactly(1001L);
    }

    @Test
    void 내_지역_요약은_요청_기기의_핀을_기간과_내용으로_걸러_시군구와_시도로_센다() {
        // given
        LocalDate today = LocalDate.of(2026, 10, 21);
        Instant yesterdayKst = Instant.parse("2026-10-20T14:59:59Z");
        var ordered = PinCandidates.of(List.of(
                EmotionMapPin.of(1L, REQUESTER, NOW, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(2L, REQUESTER, NOW, 127, 38, EmotionState.EXHAUSTED, 0, null, true, "11010540"),
                EmotionMapPin.of(3L, REQUESTER, NOW, 127, 38, EmotionState.EXHAUSTED, 0, null, false, "11020510"),
                EmotionMapPin.of(4L, REQUESTER, NOW, 127, 38, EmotionState.ANGRY, 0, null, false, "26010510"),
                EmotionMapPin.of(6L, REQUESTER, yesterdayKst, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(7L, 7L, NOW, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530")), null).ordered();

        // when
        var todayAll = ordered.regionSummaries(REQUESTER,
                EmotionSearchFilter.of(today, today, null, EmotionSearchScope.MY, null));
        var memoAllTime = ordered.regionSummaries(REQUESTER,
                EmotionSearchFilter.of(null, null, EmotionContentFilter.MEMO, EmotionSearchScope.MY, null));
        var stranger = ordered.regionSummaries(8L, EmotionSearchFilter.of(null, null, null, EmotionSearchScope.MY, null));

        // then
        assertThat(todayAll.get(RegionLevel.SIGUNGU)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "11010", RegionEmotionSummary.of(2, EmotionState.ANGRY),
                "11020", RegionEmotionSummary.of(1, EmotionState.EXHAUSTED),
                "26010", RegionEmotionSummary.of(1, EmotionState.ANGRY)));
        assertThat(todayAll.get(RegionLevel.SIDO)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "11", RegionEmotionSummary.of(3, EmotionState.EXHAUSTED),
                "26", RegionEmotionSummary.of(1, EmotionState.ANGRY)));
        assertThat(memoAllTime.get(RegionLevel.SIGUNGU)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "11010", RegionEmotionSummary.of(2, EmotionState.ANGRY),
                "11020", RegionEmotionSummary.of(1, EmotionState.EXHAUSTED),
                "26010", RegionEmotionSummary.of(1, EmotionState.ANGRY)));
        assertThat(stranger.get(RegionLevel.SIGUNGU)).isEmpty();
        assertThat(stranger.get(RegionLevel.SIDO)).isEmpty();
    }

    @Test
    void 최신순과_동시각_ID_역순으로_선택하고_조회_시각_이후의_핀은_제외한다() {
        // given
        var candidates = PinCandidates.of(List.of(
                pin(1, 1L, NOW.minusSeconds(1)), pin(2, 1L, NOW),
                pin(4, 1L, NOW.plusSeconds(1)), pin(3, 1L, NOW)), null);

        // when
        var selected = candidates.ordered().select(cursor(), REQUESTER, NO_BLOCKS, 201);

        // then
        assertThat(selected.pins()).extracting(EmotionMapPin::id).containsExactly(3L, 2L, 1L);
        assertThat(selected.scanned()).isEqualTo(4);
    }

    @Test
    void 같은_작성_시각에서도_커서_이후의_ID만_선택한다() {
        // given
        var ordered = PinCandidates.of(List.of(pin(1, 1L, NOW), pin(2, 1L, NOW), pin(3, 1L, NOW)), null).ordered();

        // when
        var selected = ordered.select(cursor().next(NOW, 3), REQUESTER, NO_BLOCKS, 201);

        // then
        assertThat(selected.pins()).extracting(EmotionMapPin::id).containsExactly(2L, 1L);
    }

    @Test
    void 차단된_앞쪽_후보를_넘어_201개를_고르고_제외한_후보도_검사수에_포함한다() {
        // given
        List<EmotionMapPin> pins = new ArrayList<>();
        for (int id = 1; id <= 220; id++) {
            pins.add(pin(1000 + id, 7L, NOW));
        }
        for (int id = 1; id <= 205; id++) {
            pins.add(pin(id, 8L, NOW.minusSeconds(1)));
        }
        var ordered = PinCandidates.of(pins, null).ordered();

        // when
        var selected = ordered.select(cursor(), REQUESTER, EmotionMapBlocks.of(Set.of(205L), Set.of(7L)), 201);
        var allBlocked = ordered.select(cursor(), REQUESTER, EmotionMapBlocks.of(Set.of(), Set.of(7L, 8L)), 201);

        // then
        assertThat(selected.pins()).hasSize(201).allSatisfy(pin -> {
            assertThat(pin.deviceId()).isEqualTo(8L);
            assertThat(pin.id()).isNotEqualTo(205L);
        });
        assertThat(selected.pins().getLast().id()).isEqualTo(4L);
        assertThat(selected.scanned()).isEqualTo(422);
        assertThat(allBlocked.pins()).isEmpty();
        assertThat(allBlocked.scanned()).isEqualTo(425);
    }

    @Test
    void 날짜변경선과_위도_경계를_포함하고_NULL_작성자와_상태를_보존한다() {
        // given
        var ordered = PinCandidates.of(List.of(
                EmotionMapPin.of(1L, null, NOW, 180, 37, null, 0, null, false, "11010530"),
                EmotionMapPin.of(2L, 2L, NOW, -180, 39, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(3L, 2L, NOW, 0, 38, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(4L, 2L, NOW, 175, 36, EmotionState.ANGRY, 0, null, false, "11010530")), null).ordered();
        var cursor = EmotionCursor.initialWithinBounds(EmotionSearchBounds.of(170, 37, -170, 39), NOW,
                EmotionSearchFilter.from(null));

        // when
        var selected = ordered.select(cursor, REQUESTER, EmotionMapBlocks.of(Set.of(), Set.of(10L)), 201);

        // then
        assertThat(selected.pins()).extracting(EmotionMapPin::id).containsExactly(2L, 1L);
        assertThat(selected.pins().getLast().state()).isNull();
        assertThat(selected.pins().getLast().deviceId()).isNull();
    }

    @Test
    void 일반_bbox의_양끝을_포함하고_바깥_핀은_제외한다() {
        // given
        var ordered = PinCandidates.of(List.of(
                EmotionMapPin.of(1L, 1L, NOW, 126, 37, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(2L, 1L, NOW, 128, 39, EmotionState.ANGRY, 0, null, false, "11010530"),
                EmotionMapPin.of(3L, 1L, NOW, 128.1, 38, EmotionState.ANGRY, 0, null, false, "11010530")), null).ordered();

        // when
        var selected = ordered.select(cursor(), REQUESTER, NO_BLOCKS, 201);

        // then
        assertThat(selected.pins()).extracting(EmotionMapPin::id).containsExactly(2L, 1L);
    }

    @Test
    void 후보_등록수정삭제와_입력_목록의_변경이_이전_후보와_정렬_결과를_바꾸지_않는다() {
        // given
        List<EmotionMapPin> input = new ArrayList<>(List.of(pin(1, 1L, NOW.minusSeconds(1))));
        var original = PinCandidates.of(input, null);
        var published = original.ordered();
        input.clear();

        // when
        var added = original.applyChange(null, contribution(2, EmotionState.ANGRY, "등록", NOW, false), NOW, 100_000);
        var updated = added.applyChange(contribution(1, EmotionState.ANGRY, "기존", NOW.minusSeconds(1), false),
                contribution(1, EmotionState.EXHAUSTED, "수정", NOW.minusSeconds(1), false), NOW, 100_000);
        var deleted = updated.applyChange(contribution(1, EmotionState.EXHAUSTED, "수정", NOW.minusSeconds(1), false),
                contribution(1, EmotionState.EXHAUSTED, "수정", NOW.minusSeconds(1), true), NOW, 100_000);

        // then
        assertThat(original.size()).isEqualTo(1);
        assertThat(original.ordered().select(cursor(), REQUESTER, NO_BLOCKS, 201).pins()).extracting(EmotionMapPin::id).containsExactly(1L);
        var oldPins = published.select(cursor(), REQUESTER, NO_BLOCKS, 201).pins();
        assertThat(oldPins).singleElement().satisfies(pin -> assertThat(pin.state()).isEqualTo(EmotionState.ANGRY));
        assertThat(updated.ordered().select(cursor(), REQUESTER, NO_BLOCKS, 201).pins()).filteredOn(pin -> pin.id().equals(1L))
                .singleElement().satisfies(pin -> assertThat(pin.state()).isEqualTo(EmotionState.EXHAUSTED));
        assertThat(deleted.ordered().select(cursor(), REQUESTER, NO_BLOCKS, 201).pins()).extracting(EmotionMapPin::id).containsExactly(2L);
        assertThatThrownBy(oldPins::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 후보_상한까지는_추가하고_초과하면_기존_후보를_변경하지_않는다() {
        // given
        var original = PinCandidates.of(List.of(pin(1, 1L, NOW)), null);
        var next = contribution(2, EmotionState.ANGRY, "등록", NOW, false);

        // when
        var atLimit = original.applyChange(null, next, NOW, 2);
        var exceeded = original.applyChange(null, next, NOW, 1);

        // then
        assertThat(atLimit.size()).isEqualTo(2);
        assertThat(exceeded).isNull();
        assertThat(original.size()).isEqualTo(1);
    }

    @Test
    void 내용없는_감정은_후보에서_빠지고_미래_감정은_가장_이른_활성시각만_갱신한다() {
        // given
        var original = PinCandidates.of(List.of(pin(1, 1L, NOW)), NOW.plusSeconds(5));

        // when
        var removed = original.applyChange(contribution(1, EmotionState.ANGRY, "기존", NOW, false),
                contribution(1, EmotionState.ANGRY, null, NOW, false), NOW, 100_000);
        var later = removed.applyChange(null, contribution(2, EmotionState.ANGRY, "미래", NOW.plusSeconds(10), false), NOW, 100_000);
        var earlier = later.applyChange(null, contribution(3, EmotionState.ANGRY, "미래", NOW.plusSeconds(3), false), NOW, 100_000);

        // then
        assertThat(removed.size()).isZero();
        assertThat(later.nextActivation()).isEqualTo(NOW.plusSeconds(5));
        assertThat(earlier.nextActivation()).isEqualTo(NOW.plusSeconds(3));
        assertThat(earlier.size()).isZero();
        assertThat(original.size()).isEqualTo(1);
        assertThat(original.nextActivation()).isEqualTo(NOW.plusSeconds(5));
    }

    private EmotionMapPin pin(long id, Long deviceId, Instant at) {
        return EmotionMapPin.of(id, deviceId, at, 127, 38, EmotionState.ANGRY, 0, null, false, "11010530");
    }

    private EmotionCursor cursor() {
        return EmotionCursor.initialWithinBounds(BOUNDS, NOW, EmotionSearchFilter.from(null));
    }

    private EmotionCursor cursor(EmotionContentFilter contentType, EmotionSearchScope scope) {
        return EmotionCursor.initialWithinBounds(BOUNDS, NOW, EmotionSearchFilter.of(null, null, contentType, scope, null));
    }

    private EmotionMapContribution contribution(long id, EmotionState state, String memo, Instant at, boolean deleted) {
        Emotion emotion = 기본_한숨_빌더()
                .state(state)
                .memo(memo)
                .build();
        ReflectionTestUtils.setField(emotion, "id", id);
        ReflectionTestUtils.setField(emotion, "createdAt", at);
        if (deleted) {
            emotion.delete();
        }

        return EmotionMapContribution.from(emotion);
    }

    private EmotionMapContribution contribution(long id, Long deviceId, Instant at) {
        return EmotionMapContribution.from(emotionOf(id, deviceId, at));
    }

    private EmotionMapContribution deletedContribution(long id, Long deviceId, Instant at) {
        Emotion emotion = emotionOf(id, deviceId, at);
        emotion.delete();

        return EmotionMapContribution.from(emotion);
    }

    private Emotion emotionOf(long id, Long deviceId, Instant at) {
        Emotion emotion = 기본_한숨_빌더().deviceId(deviceId).memo("기록").build();
        ReflectionTestUtils.setField(emotion, "id", id);
        ReflectionTestUtils.setField(emotion, "createdAt", at);

        return emotion;
    }
}
