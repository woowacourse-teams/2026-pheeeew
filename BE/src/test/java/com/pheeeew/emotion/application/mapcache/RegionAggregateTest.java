package com.pheeeew.emotion.application.mapcache;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RegionAggregateTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    @Test
    void 시군구를_시도로_합산하고_NULL_상태와_동률_우선순위를_보존한다() {
        // given
        var aggregate = RegionAggregate.from(List.of(
                RegionEmotionStateCount.of("11010", EmotionState.ANGRY, 2, null),
                RegionEmotionStateCount.of("11010", null, 1, null),
                RegionEmotionStateCount.of("11020", EmotionState.EXHAUSTED, 2, null),
                RegionEmotionStateCount.of("26010", null, 3, null)));

        // when
        var summaries = aggregate.summaries();

        // then
        assertThat(summaries.get(RegionLevel.SIGUNGU)).containsEntry("11010", RegionEmotionSummary.of(3, EmotionState.ANGRY))
                .containsEntry("11020", RegionEmotionSummary.of(2, EmotionState.EXHAUSTED));
        assertThat(summaries.get(RegionLevel.SIDO)).containsEntry("11", RegionEmotionSummary.of(5, EmotionState.ANGRY))
                .containsEntry("26", RegionEmotionSummary.of(3, null));
    }

    @Test
    void 현재_개수가_0인_지역도_가장_이른_미래_활성_시각은_보존한다() {
        // given / when
        var aggregate = RegionAggregate.from(List.of(
                RegionEmotionStateCount.of("11010", EmotionState.ANGRY, 0, NOW.plusSeconds(20)),
                RegionEmotionStateCount.of("11020", EmotionState.EXHAUSTED, 0, NOW.plusSeconds(10))));

        // then
        assertThat(aggregate.summaries().get(RegionLevel.SIGUNGU)).isEmpty();
        assertThat(aggregate.summaries().get(RegionLevel.SIDO)).isEmpty();
        assertThat(aggregate.nextActivation()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void 상태_변경은_새_집계에만_반영하고_이전_집계와_공개된_요약은_보존한다() {
        // given
        var original = RegionAggregate.from(List.of(RegionEmotionStateCount.of("11010", null, 1, null)));
        var published = original.summaries();
        var before = contribution(null, "기존 기록", NOW.minusSeconds(1), false);
        var after = contribution(EmotionState.EXHAUSTED, "수정 기록", NOW.minusSeconds(1), false);

        // when
        var updated = original.applyChange(before, after, NOW);

        // then
        assertThat(updated.summaries().get(RegionLevel.SIDO)).containsEntry("11", RegionEmotionSummary.of(1, EmotionState.EXHAUSTED));
        assertThat(original.summaries()).isEqualTo(published);
        assertThat(published.get(RegionLevel.SIDO)).containsEntry("11", RegionEmotionSummary.of(1, null));
        assertThatThrownBy(() -> published.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> published.get(RegionLevel.SIDO).clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 마지막_감정을_삭제하면_시군구와_시도_요약에서_모두_제거한다() {
        // given
        var original = RegionAggregate.from(List.of(RegionEmotionStateCount.of("11010", EmotionState.ANGRY, 1, null)));
        var before = contribution(EmotionState.ANGRY, "기록", NOW.minusSeconds(1), false);
        var after = contribution(EmotionState.ANGRY, "기록", NOW.minusSeconds(1), true);

        // when
        var deleted = original.applyChange(before, after, NOW);

        // then
        assertThat(deleted.summaries().get(RegionLevel.SIGUNGU)).isEmpty();
        assertThat(deleted.summaries().get(RegionLevel.SIDO)).isEmpty();
        assertThat(original.summaries().get(RegionLevel.SIGUNGU)).hasSize(1);
    }

    @Test
    void 내용이_없거나_삭제된_감정은_새_기여분에_포함하지_않는다() {
        // given
        var empty = RegionAggregate.from(List.of());

        // when
        var withoutContent = empty.applyChange(null, contribution(EmotionState.ANGRY, null, NOW, false), NOW);
        var deleted = withoutContent.applyChange(null, contribution(EmotionState.ANGRY, "기록", NOW, true), NOW);

        // then
        assertThat(deleted.summaries().get(RegionLevel.SIGUNGU)).isEmpty();
        assertThat(deleted.summaries().get(RegionLevel.SIDO)).isEmpty();
        assertThat(deleted.nextActivation()).isNull();
    }

    @Test
    void 미래_감정은_현재_개수에서_제외하고_기존보다_이른_활성_시각만_반영한다() {
        // given
        var original = RegionAggregate.from(List.of(
                RegionEmotionStateCount.of("11010", EmotionState.ANGRY, 0, NOW.plusSeconds(5))));

        // when
        var later = original.applyChange(null, contribution(EmotionState.ANGRY, "나중 기록", NOW.plusSeconds(10), false), NOW);
        var earlier = later.applyChange(null, contribution(EmotionState.ANGRY, "먼저 기록", NOW.plusSeconds(3), false), NOW);

        // then
        assertThat(later.nextActivation()).isEqualTo(NOW.plusSeconds(5));
        assertThat(earlier.nextActivation()).isEqualTo(NOW.plusSeconds(3));
        assertThat(earlier.summaries().get(RegionLevel.SIGUNGU)).isEmpty();
        assertThat(original.nextActivation()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void 존재하지_않는_기여분을_제거하면_음수_집계를_허용하지_않는다() {
        // given
        var empty = RegionAggregate.from(List.of());
        var before = contribution(EmotionState.ANGRY, "기록", NOW, false);

        // when / then
        assertThatThrownBy(() -> empty.applyChange(before, null, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessage("지역 집계 기여분이 음수입니다.");
        assertThat(empty.summaries().get(RegionLevel.SIDO)).isEmpty();
    }

    private EmotionMapContribution contribution(EmotionState state, String memo, Instant createdAt, boolean deleted) {
        Emotion emotion = 기본_한숨_빌더().state(state).memo(memo).build();
        ReflectionTestUtils.setField(emotion, "createdAt", createdAt);
        if (deleted) {
            emotion.delete();
        }

        return EmotionMapContribution.from(emotion);
    }
}
