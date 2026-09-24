package com.pheeeew.emotion.domain;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmotionTest {

    @Test
    void 좋아요_수를_한_개씩_증가시킨다() {
        // given
        Emotion emotion = 기본_한숨_빌더().build();

        // when
        emotion.increaseLikeCount();
        emotion.increaseLikeCount();

        // then
        assertThat(emotion.getLikeCount()).isEqualTo(2);
    }

    @Test
    void 좋아요_수를_한_개_감소시킨다() {
        // given
        Emotion emotion = 기본_한숨_빌더().build();
        emotion.increaseLikeCount();
        emotion.increaseLikeCount();

        // when
        emotion.decreaseLikeCount();

        // then
        assertThat(emotion.getLikeCount()).isOne();
    }

    @Test
    void 좋아요_수가_0이면_감소시킬_수_없다() {
        // given
        Emotion emotion = 기본_한숨_빌더().build();

        // when
        Throwable throwable = catchThrowable(emotion::decreaseLikeCount);

        // then
        assertThat(throwable).isInstanceOf(IllegalStateException.class);
        assertThat(emotion.getLikeCount()).isZero();
    }

    @Test
    void 삭제하면_삭제_시각이_기록된다() {
        // given
        Emotion emotion = 기본_한숨_빌더().build();

        // when
        emotion.delete();

        // then
        assertThat(emotion.getDeletedAt()).isNotNull();
    }

    @Test
    void 이미_삭제한_한숨을_다시_삭제해도_최초_삭제_시각을_유지한다() {
        // given
        Emotion emotion = 기본_한숨_빌더().build();
        emotion.delete();
        Instant 최초_삭제_시각 = emotion.getDeletedAt();

        // when
        emotion.delete();

        // then
        assertThat(emotion.getDeletedAt()).isEqualTo(최초_삭제_시각);
    }

    @Test
    void 메모의_앞뒤_공백을_제거한다() {
        // given
        String memo = "  오늘은 힘들었다  ";

        // when
        Emotion emotion = 기본_한숨_빌더()
                .memo(memo)
                .build();

        // then
        assertThat(emotion.getMemo()).isEqualTo("오늘은 힘들었다");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 빈_문자열과_공백으로만_이루어진_메모는_null로_변환한다(String memo) {
        // given
        String requestedMemo = memo;

        // when
        Emotion emotion = 기본_한숨_빌더()
                .memo(requestedMemo)
                .build();

        // then
        assertThat(emotion.getMemo()).isNull();
    }

    @Test
    void 메모는_200자를_초과하면_생성할_수_없다() {
        // given
        String memo = "가".repeat(201);

        // when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더()
                .memo(memo)
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메모는 200자를 초과할 수 없습니다.");
    }

    @Test
    void 메모의_글자_수는_유니코드_코드포인트로_센다() {
        // given
        String memo = "😀".repeat(200);

        // when
        Emotion emotion = 기본_한숨_빌더().memo(memo).build();

        // then
        assertThat(emotion.getMemo()).isEqualTo(memo);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 23.5, 359.999})
    void 스탬프_각도는_0도_이상_360도_미만으로_저장한다(double rotationDegrees) {
        // when
        Emotion emotion = 기본_한숨_빌더()
                .rotationDegrees(rotationDegrees)
                .build();

        // then
        assertThat(emotion.getRotationDegrees()).isEqualTo(rotationDegrees);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 360.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 범위를_벗어나거나_유한하지_않은_스탬프_각도는_거부한다(double rotationDegrees) {
        // when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더()
                .rotationDegrees(rotationDegrees)
                .build());

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("스탬프 각도는 0도 이상 360도 미만이어야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 닉네임은_null이거나_비어_있으면_생성할_수_없다(String nickname) {
        // given
        String requestedNickname = nickname;

        // when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더()
                .nickname(requestedNickname)
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("닉네임은 비어 있을 수 없습니다.");
    }

    @Test
    void 닉네임은_50자를_초과하면_생성할_수_없다() {
        // given
        String nickname = "가".repeat(51);

        // when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더()
                .nickname(nickname)
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("닉네임은 50자를 초과할 수 없습니다.");
    }

    @Test
    void 위치는_WGS84_좌표가_아니면_생성할_수_없다() {
        // given
        int invalidSrid = 0;

        // when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더()
                .location(서울시청_좌표(invalidSrid))
                .build()
        );

        // then
        assertThat(throwable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("위치는 비어 있지 않은 WGS84(SRID 4326) 점 좌표여야 합니다.");
    }
}
