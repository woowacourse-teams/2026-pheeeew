package com.pheeeew.emotion.domain.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class EmotionSearchFilterTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void 필터를_생략하면_기간_제한없이_전체_내용과_범위를_조회한다() {
        // given / when
        EmotionSearchFilter filter = EmotionSearchFilter.from(null);

        // then
        assertThat(filter.contentType()).isEqualTo(EmotionContentFilter.ALL);
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.ALL);
        assertThat(filter.groupIds()).isEmpty();
        assertThat(filter.startInclusive()).isNull();
        assertThat(filter.endExclusive()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = EmotionSearchScope.class, names = "GROUP")
    void 그룹_하나를_담은_목록도_그룹_범위로_해석한다(EmotionSearchScope scope) {
        // given / when
        EmotionSearchFilter filter = EmotionSearchFilter.of(null, null, null, scope, List.of(GROUP_ID));

        // then
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.GROUP);
        assertThat(filter.groupIds()).containsExactly(GROUP_ID);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = EmotionSearchScope.class, names = "GROUP")
    void 그룹_목록은_순서와_중복에_관계없이_같은_필터로_정규화한다(EmotionSearchScope scope) {
        // given
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 11);

        // when
        EmotionSearchFilter filter = EmotionSearchFilter.of(start, end, EmotionContentFilter.MEMO, scope,
                List.of(OTHER_GROUP_ID, GROUP_ID, OTHER_GROUP_ID));

        // then
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.GROUP);
        assertThat(filter.groupIds()).containsExactly(GROUP_ID, OTHER_GROUP_ID);
        assertThat(filter).isEqualTo(EmotionSearchFilter.of(start, end, EmotionContentFilter.MEMO,
                EmotionSearchScope.GROUP, List.of(GROUP_ID, OTHER_GROUP_ID)));
        assertThat(filter.startDate()).isEqualTo(start);
        assertThat(filter.endDate()).isEqualTo(end);
        assertThat(filter.contentType()).isEqualTo(EmotionContentFilter.MEMO);
    }

    @Test
    void 그룹_목록은_입력_목록과_반환_목록의_변경으로부터_보호한다() {
        // given
        List<UUID> groupIds = new ArrayList<>(List.of(GROUP_ID));

        // when
        EmotionSearchFilter filter = EmotionSearchFilter.from(groupIds);
        groupIds.add(OTHER_GROUP_ID);

        // then
        assertThat(filter.contentType()).isEqualTo(EmotionContentFilter.ALL);
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.GROUP);
        assertThat(filter.groupIds()).containsExactly(GROUP_ID);
        assertThatThrownBy(() -> filter.groupIds().add(OTHER_GROUP_ID))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidGroupSelections")
    void 명시한_그룹_목록이_비어_있거나_null_ID를_포함하면_거부한다(List<UUID> groupIds) {
        // given / when / then
        assertThatThrownBy(() -> EmotionSearchFilter.of(null, null, null, null, groupIds))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmotionSearchFilter.from(groupIds))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기본_그룹_필터도_전체_내용과_기간_제한_없이_그룹을_정규화한다() {
        // given / when
        EmotionSearchFilter filter = EmotionSearchFilter.from(List.of(OTHER_GROUP_ID, GROUP_ID, OTHER_GROUP_ID));

        // then
        assertThat(filter.contentType()).isEqualTo(EmotionContentFilter.ALL);
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.GROUP);
        assertThat(filter.startDate()).isNull();
        assertThat(filter.endDate()).isNull();
        assertThat(filter.groupIds()).containsExactly(GROUP_ID, OTHER_GROUP_ID);
    }

    @ParameterizedTest
    @EnumSource(value = EmotionSearchScope.class, names = {"ALL", "MY"})
    void 전체나_본인_범위에는_그룹_목록을_전달할_수_없다(EmotionSearchScope scope) {
        // given / when / then
        assertThatThrownBy(() -> EmotionSearchFilter.of(null, null, null, scope,
                List.of(GROUP_ID, OTHER_GROUP_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(EmotionContentFilter.class)
    void 날짜와_내용_및_본인_범위를_함께_유지한다(EmotionContentFilter contentType) {
        // given
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 11);

        // when
        EmotionSearchFilter filter = EmotionSearchFilter.of(start, end, contentType, EmotionSearchScope.MY, null);

        // then
        assertThat(filter.startDate()).isEqualTo(start);
        assertThat(filter.endDate()).isEqualTo(end);
        assertThat(filter.contentType()).isEqualTo(contentType);
        assertThat(filter.scope()).isEqualTo(EmotionSearchScope.MY);
        assertThat(filter.groupIds()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-11, 2026-10-11, 2026-10-10T15:00:00Z, 2026-10-11T15:00:00Z",
            "2026-10-01, 2026-10-11, 2026-09-30T15:00:00Z, 2026-10-11T15:00:00Z",
            "2024-02-29, 2024-02-29, 2024-02-28T15:00:00Z, 2024-02-29T15:00:00Z"
    })
    void 한국_시간의_양끝_날짜를_포함하는_반개방_구간으로_변환한다(
            LocalDate start, LocalDate end, Instant startInclusive, Instant endExclusive) {
        // given / when
        EmotionSearchFilter filter = EmotionSearchFilter.of(start, end, null, null, null);

        // then
        assertThat(filter.startInclusive()).isEqualTo(startInclusive);
        assertThat(filter.endExclusive()).isEqualTo(endExclusive);
    }

    @ParameterizedTest
    @MethodSource("invalidDates")
    void 한쪽_날짜_누락과_역전되거나_변환할_수_없는_범위를_거부한다(LocalDate start, LocalDate end) {
        // given / when / then
        assertThatThrownBy(() -> EmotionSearchFilter.of(start, end, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidScopes")
    void 조회_범위와_그룹_ID가_맞지_않으면_거부한다(EmotionSearchScope scope, UUID groupId) {
        // given / when / then
        assertThatThrownBy(() -> EmotionSearchFilter.of(null, null, null, scope, groupId == null ? null : List.of(groupId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidDates() {
        LocalDate date = LocalDate.of(2026, 10, 11);
        return Stream.of(Arguments.of(date, null), Arguments.of(null, date),
                Arguments.of(date, date.minusDays(1)), Arguments.of(date, LocalDate.MAX));
    }

    private static Stream<Arguments> invalidGroupSelections() {
        return Stream.of(Arguments.of(List.of()), Arguments.of(Arrays.asList(GROUP_ID, null)));
    }

    private static Stream<Arguments> invalidScopes() {
        return Stream.of(Arguments.of(EmotionSearchScope.GROUP, null),
                Arguments.of(EmotionSearchScope.ALL, GROUP_ID), Arguments.of(EmotionSearchScope.MY, GROUP_ID));
    }
}
