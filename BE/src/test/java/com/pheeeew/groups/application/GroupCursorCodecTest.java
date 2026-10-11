package com.pheeeew.groups.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.groups.application.dto.GroupCursor;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GroupCursorCodecTest {

    private static final Instant 기준_시각 = Instant.parse("2026-10-11T09:00:00Z");

    @Test
    void 탐색_커서를_왕복하면_생성_시각과_식별자를_복원한다() {
        // given
        String 커서 = GroupCursorCodec.encodeExplore(기준_시각, 42L);

        // when
        GroupCursor 복원 = GroupCursorCodec.decodeExploreOrInitial(커서);

        // then
        assertThat(복원.lastCreatedAt()).isEqualTo(기준_시각);
        assertThat(복원.lastId()).isEqualTo(42L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 탐색_커서가_없으면_가장_먼_미래부터_읽는다(String 커서) {
        // given / when
        GroupCursor 복원 = GroupCursorCodec.decodeExploreOrInitial(커서);

        // then
        assertThat(복원.lastCreatedAt()).isEqualTo(Instant.parse("9999-12-31T23:59:59Z"));
        assertThat(복원.lastId()).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1|2026-10-11T09:00:00Z",
            "1|2026-10-11T09:00:00Z|42|7",
            "9|2026-10-11T09:00:00Z|42",
            "1|2026-10-11T09:00:00Z|0",
            "1|2026-10-11T09:00:00Z|-1",
            "1|2026-10-11T09:00:00Z|abc",
            "1|2026-13-99|42",
            "1||42"
    })
    void 사용할_수_없는_내용을_가진_탐색_커서는_거부한다(String 평문) {
        // given
        String 커서 = 커서로_감싼다(평문);

        // when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeExploreOrInitial(커서));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-cursor", "!!!"})
    void 커서_형식이_올바르지_않으면_거부한다(String 커서) {
        // given / when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeExploreOrInitial(커서));
    }

    @Test
    void 너무_긴_커서는_디코딩하지_않고_거부한다() {
        // given
        String 커서 = "A".repeat(129);

        // when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeExploreOrInitial(커서));
    }

    @Test
    void 멤버_커서를_왕복하면_가입_시각과_식별자를_복원한다() {
        // given
        String 커서 = GroupCursorCodec.encodeMembers(기준_시각, 7L);

        // when
        GroupCursor 복원 = GroupCursorCodec.decodeMembersOrInitial(커서);

        // then
        assertThat(복원.lastCreatedAt()).isEqualTo(기준_시각);
        assertThat(복원.lastId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 멤버_커서가_없으면_에포크부터_읽는다(String 커서) {
        // given / when
        GroupCursor 복원 = GroupCursorCodec.decodeMembersOrInitial(커서);

        // then
        assertThat(복원.lastCreatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(복원.lastId()).isZero();
    }

    @Test
    void 탐색_커서를_멤버_목록에_쓰면_거부한다() {
        // given
        String 탐색_커서 = GroupCursorCodec.encodeExplore(기준_시각, 42L);

        // when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeMembersOrInitial(탐색_커서));
    }

    @Test
    void 멤버_커서를_탐색_목록에_쓰면_거부한다() {
        // given
        String 멤버_커서 = GroupCursorCodec.encodeMembers(기준_시각, 7L);

        // when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeExploreOrInitial(멤버_커서));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2|2026-10-11T09:00:00Z",
            "2|2026-10-11T09:00:00Z|0",
            "2|2026-13-99|7"
    })
    void 사용할_수_없는_내용을_가진_멤버_커서는_거부한다(String 평문) {
        // given
        String 커서 = 커서로_감싼다(평문);

        // when / then
        잘못된_커서임을_검증한다(() -> GroupCursorCodec.decodeMembersOrInitial(커서));
    }

    private String 커서로_감싼다(String 평문) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(평문.getBytes(StandardCharsets.UTF_8));
    }

    private void 잘못된_커서임을_검증한다(ThrowingCallable 디코딩) {
        assertThatThrownBy(디코딩)
                .isInstanceOf(GroupException.class)
                .hasMessage("그룹 목록 커서를 사용할 수 없습니다.")
                .extracting(throwable -> ((GroupException) throwable).getErrorCode())
                .isEqualTo(GroupErrorCode.GROUP_INVALID_CURSOR);
    }
}
