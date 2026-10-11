package com.pheeeew.emotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

class EmotionCursorCodecTest {

    private static final Instant SNAPSHOT = Instant.parse("2026-10-08T01:00:00.123456Z");
    private static final Instant LAST_CREATED_AT = Instant.parse("2026-10-07T01:00:00.654321Z");

    @Test
    void 커서를_인코딩하고_디코딩하면_검색_조건을_복원한다() {
        // given
        EmotionCursor cursor = EmotionCursor.ofWithinBounds(
                EmotionSearchBounds.of(126.9, 37.5, 127.1, 37.6),
                Instant.parse("2026-09-03T03:00:00.123456Z"),
                Instant.parse("2026-09-01T12:00:00.654321Z"),
                42L,
                null
        );

        // when
        String encoded = EmotionCursorCodec.encode(cursor);
        EmotionCursor decoded = EmotionCursorCodec.decodeWithinBounds(encoded);
        String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);

        // then
        assertThat(payload).isEqualTo(
                "1|126.9|37.5|127.1|37.6"
                        + "|2026-09-03T03:00:00.123456Z|2026-09-01T12:00:00.654321Z|42"
        );
        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    void 그룹_필터는_인코딩과_다음_페이지에서도_유지된다() {
        // given
        UUID groupId = UUID.randomUUID();
        var initial = EmotionCursor.initialWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                Instant.parse("2026-09-25T00:00:00Z"), groupId);

        // when
        var next = EmotionCursorCodec.decodeWithinBounds(EmotionCursorCodec.encode(initial))
                .next(Instant.parse("2026-09-24T00:00:00Z"), 42L);

        // then
        assertThat(next.groupId()).isEqualTo(groupId);
        assertThat(EmotionCursorCodec.decodeWithinBounds(EmotionCursorCodec.encode(next))).isEqualTo(next);
        assertThat(new String(Base64.getUrlDecoder().decode(EmotionCursorCodec.encode(next)), StandardCharsets.UTF_8))
                .isEqualTo("2|126.0|37.0|128.0|38.0|2026-09-25T00:00:00Z|2026-09-24T00:00:00Z|42|" + groupId);
    }

    @Test
    void 기존_커서의_생성_경로는_좌표를_생략할_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> EmotionCursor.initialWithinBounds(null, SNAPSHOT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EmotionCursor.ofWithinBounds(null, SNAPSHOT, LAST_CREATED_AT, 42L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-cursor"})
    void 커서_형식이_올바르지_않으면_거부한다(String encoded) {
        // given / when / then
        잘못된_커서임을_검증한다(encoded);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|42",
            "2|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|42|not-a-group-id",
            "1|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z",
            "1|126.9|37.5|126.9|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|42",
            "1|126.9|37.5|127.1|37.6|2026-09-01T12:00:00Z|2026-09-03T03:00:00Z|42",
            "1|126.9|37.5|127.1|37.6|2026-09-03T03:00:00Z|2026-09-01T12:00:00Z|0"
    })
    void 사용할_수_없는_내용을_가진_커서는_거부한다(String payload) {
        // given
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        잘못된_커서임을_검증한다(encoded);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "f6ebff29-9f33-40fc-9779-878710f3f163")
    void 전체와_그룹_범위는_왕복_변환과_다음_커서에서도_유지된다(String group) {
        // given
        UUID groupId = group == null ? null : UUID.fromString(group);
        EmotionCursor initial = EmotionCursor.initialWithoutBounds(SNAPSHOT, groupId);

        // when
        EmotionCursor restored = EmotionCursorCodec.decodeFilteredWithoutBounds(EmotionCursorCodec.encode(initial));
        EmotionCursor next = restored.next(LAST_CREATED_AT, 42L);
        String encoded = EmotionCursorCodec.encode(next);

        // then
        assertThat(restored).isEqualTo(initial);
        assertThat(restored.bounds()).isNull();
        assertThat(initial.lastItemCreatedAt()).isEqualTo(SNAPSHOT);
        assertThat(initial.lastId()).isEqualTo(Long.MAX_VALUE);
        assertThat(next).isEqualTo(EmotionCursor.ofWithoutBounds(SNAPSHOT, LAST_CREATED_AT, 42L, groupId));
        assertThat(EmotionCursorCodec.decodeFilteredWithoutBounds(encoded)).isEqualTo(next);
        assertThat(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8))
                .isEqualTo("3|" + SNAPSHOT + "|" + LAST_CREATED_AT + "|42|"
                        + (group == null ? "" : group));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "f6ebff29-9f33-40fc-9779-878710f3f163")
    void 좌표가_있는_커서와_없는_커서는_양방향으로_혼용할_수_없다(String group) {
        // given
        UUID groupId = group == null ? null : UUID.fromString(group);
        EmotionCursor legacy = EmotionCursor.initialWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                SNAPSHOT, groupId);
        String legacyEncoded = EmotionCursorCodec.encode(legacy);
        String withoutBoundsEncoded = EmotionCursorCodec.encode(EmotionCursor.initialWithoutBounds(SNAPSHOT, groupId));

        // when / then
        잘못된_좌표_없는_커서임을_검증한다(legacyEncoded);
        assertThatThrownBy(() -> EmotionCursorCodec.decodeWithinBounds(withoutBoundsEncoded))
                .isInstanceOf(EmotionException.class)
                .extracting(exception -> ((EmotionException) exception).getErrorCode())
                .isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-cursor", "%%%"})
    void 비어_있거나_잘못_인코딩된_커서를_거부한다(String encoded) {
        // given / when / then
        잘못된_좌표_없는_커서임을_검증한다(encoded);
    }

    @Test
    void 길이_제한을_초과한_커서를_거부한다() {
        // given / when / then
        잘못된_좌표_없는_커서임을_검증한다("a".repeat(2_049));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42|",
            "4|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42||",
            "3|invalid|2026-10-07T00:00:00Z|42|",
            "3|2026-10-08T00:00:00Z|invalid|42|",
            "3|2026-10-08T00:00:00Z|2026-10-09T00:00:00Z|42|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|0|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|-1|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|9223372036854775808|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|invalid|",
            "3|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42|invalid"
    })
    void 범위나_마지막_항목을_복원할_수_없는_커서를_거부한다(String payload) {
        // given
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        잘못된_좌표_없는_커서임을_검증한다(encoded);
    }

    @Test
    void 새_커서는_필터와_스냅샷을_다음_페이지까지_보존하고_지도_커서와_혼용하지_않는다() {
        // given
        UUID groupId = UUID.randomUUID();
        EmotionSearchFilter filter = EmotionSearchFilter.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8),
                EmotionContentFilter.MEMO, EmotionSearchScope.GROUP, groupId);
        EmotionCursor initial = EmotionCursor.initialWithoutBounds(SNAPSHOT, groupId).withFilter(filter);

        // when
        String encoded = EmotionCursorCodec.encode(initial);
        EmotionCursor restored = EmotionCursorCodec.decodeFilteredWithoutBounds(encoded);
        EmotionCursor next = restored.next(LAST_CREATED_AT, 42L);

        // then
        assertThat(restored).isEqualTo(initial);
        assertThat(next.filter()).isEqualTo(filter);
        assertThat(next.snapshotAt()).isEqualTo(SNAPSHOT);
        assertThat(next.lastItemCreatedAt()).isEqualTo(LAST_CREATED_AT);
        assertThat(next.lastId()).isEqualTo(42L);
        assertThat(EmotionCursorCodec.decodeFilteredWithoutBounds(EmotionCursorCodec.encode(next))).isEqualTo(next);
        잘못된_커서임을_검증한다(encoded);
    }

    @ParameterizedTest
    @EnumSource(EmotionSearchScope.class)
    void 기간을_생략한_녹음_필터도_전체_그룹_본인_범위를_유지한다(EmotionSearchScope scope) {
        // given
        UUID groupId = scope == EmotionSearchScope.GROUP ? UUID.randomUUID() : null;
        EmotionSearchFilter filter = EmotionSearchFilter.of(null, null, EmotionContentFilter.AUDIO, scope, groupId);
        EmotionCursor cursor = EmotionCursor.initialWithoutBounds(SNAPSHOT, groupId).withFilter(filter);

        // when / then
        assertThat(EmotionCursorCodec.decodeFilteredWithoutBounds(EmotionCursorCodec.encode(cursor))).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"omit", "same", "start", "end", "partial", "content", "scope", "group"})
    void 다음_요청의_생략과_동일_조건은_허용하고_명시된_변경은_거부한다(String change) {
        // given
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 8);
        UUID groupId = UUID.randomUUID();
        EmotionCursor cursor = EmotionCursor.initialWithoutBounds(SNAPSHOT, groupId).withFilter(
                EmotionSearchFilter.of(start, end, EmotionContentFilter.MEMO, EmotionSearchScope.GROUP, groupId));

        // when / then
        if (change.equals("omit")) {
            cursor.validateRequestedFilter(null, null, null, null, null);
        } else if (change.equals("same")) {
            cursor.validateRequestedFilter(start, end, EmotionContentFilter.MEMO, EmotionSearchScope.GROUP, groupId);
        } else {
            assertThatThrownBy(() -> cursor.validateRequestedFilter(change.equals("start") ? start.plusDays(1) : start,
                    change.equals("partial") ? null : change.equals("end") ? end.plusDays(1) : end,
                    change.equals("content") ? EmotionContentFilter.ALL : EmotionContentFilter.MEMO,
                    change.equals("scope") ? EmotionSearchScope.ALL : EmotionSearchScope.GROUP,
                    change.equals("group") ? UUID.randomUUID() : groupId))
                    .isInstanceOfSatisfying(EmotionException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"||NONE|ALL", "||MEMO|INVALID", "||MEMO|GROUP", "2026-10-01||MEMO|ALL",
            "2026-10-08|2026-10-01|MEMO|ALL", "bad|2026-10-01|MEMO|ALL", "||MEMO|ALL|extra"})
    void 새_커서의_필터나_필드_수가_잘못되면_거부한다(String filterFields) {
        // given
        String payload = "5|" + SNAPSHOT + "|" + LAST_CREATED_AT + "|42||" + filterFields;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        assertThatThrownBy(() -> EmotionCursorCodec.decodeFilteredWithoutBounds(encoded))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5|bad|2026-10-07T00:00:00Z|42||||MEMO|ALL",
            "5|2026-10-08T00:00:00Z|2026-10-09T00:00:00Z|42||||MEMO|ALL",
            "5|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|0||||MEMO|ALL",
            "5|2026-10-08T00:00:00Z|2026-10-07T00:00:00Z|42|bad|||MEMO|GROUP"
    })
    void 새_커서도_시각과_ID_및_그룹을_검증한다(String payload) {
        // given
        assertThat(payload.split("\\|", -1)).hasSize(9);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        assertThatThrownBy(() -> EmotionCursorCodec.decodeFilteredWithoutBounds(encoded))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
    }

    @Test
    void 기존_지도_커서에는_지원하지_않는_내용_필터를_인코딩하지_않는다() {
        // given
        EmotionCursor cursor = EmotionCursor.initialWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), SNAPSHOT, null)
                .withFilter(EmotionSearchFilter.of(null, null, EmotionContentFilter.MEMO, EmotionSearchScope.ALL, null));

        // when / then
        assertThatThrownBy(() -> EmotionCursorCodec.encode(cursor))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
    }

    private void 잘못된_좌표_없는_커서임을_검증한다(String encoded) {
        assertThatThrownBy(() -> EmotionCursorCodec.decodeFilteredWithoutBounds(encoded))
                .isInstanceOf(EmotionException.class)
                .extracting(exception -> ((EmotionException) exception).getErrorCode())
                .isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR);
    }

    private void 잘못된_커서임을_검증한다(String encoded) {
        assertThatThrownBy(() -> EmotionCursorCodec.decodeWithinBounds(encoded))
                .isInstanceOf(EmotionException.class)
                .extracting(exception -> ((EmotionException) exception).getErrorCode())
                .isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR);
    }
}
