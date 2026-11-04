package com.pheeeew.emotion.domain;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_STATE_CHANGE_NOT_ALLOWED;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

class EmotionTest {

    @Test
    void 익명_여부를_생략하거나_null로_주면_익명으로_생성한다() {
        // given / when
        Emotion omitted = 기본_한숨_빌더().build();
        Emotion explicitNull = 기본_한숨_빌더().anonymous(null).build();

        // then
        assertThat(omitted.isAnonymous()).isTrue();
        assertThat(explicitNull.isAnonymous()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 작성_기기가_있으면_선택한_익명_여부를_보존한다(boolean anonymous) {
        // given / when
        Emotion emotion = 기본_한숨_빌더().deviceId(1L).anonymous(anonymous).build();

        // then
        assertThat(emotion.isAnonymous()).isEqualTo(anonymous);
        assertThat(emotion.getDeviceId()).isEqualTo(1L);
    }

    @Test
    void 작성_기기가_없는_기명_감정은_생성할_수_없다() {
        // given / when
        Throwable throwable = catchThrowable(() -> 기본_한숨_빌더().anonymous(false).build());

        // then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기명 감정에는 작성 기기가 필요합니다.");
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

    @ParameterizedTest
    @ValueSource(strings = {"regionCode", "regionClassifiedAt"})
    void 지역_코드와_분류_시각은_null이면_생성할_수_없다(String field) {
        // given
        var builder = 기본_한숨_빌더();
        if (field.equals("regionCode")) {
            builder.regionCode(null);
        } else {
            builder.regionClassifiedAt(null);
        }

        // when / then
        assertThat(catchThrowable(builder::build)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EmotionState.class, names = "FRUSTRATED", mode = EnumSource.Mode.EXCLUDE)
    void 등록한_감정_종류를_다르게_수정할_수_없고_기존_내용을_보존한다(EmotionState requested) {
        // given
        Emotion emotion = 기본_한숨_빌더().state(EmotionState.FRUSTRATED).memo("기존 메모").build();
        EmotionContent replacement = EmotionContent.builder().memo("수정 메모").build();

        // when / then
        assertThatThrownBy(() -> emotion.update(requested, replacement, null))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_STATE_CHANGE_NOT_ALLOWED));
        assertThat(emotion.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(emotion.getMemo()).isEqualTo("기존 메모");
    }

    @Test
    void 같은_감정_종류로는_내용을_수정할_수_있다() {
        // given
        Emotion emotion = 기본_한숨_빌더().state(EmotionState.FRUSTRATED).memo("기존 메모").build();

        // when
        emotion.update(EmotionState.FRUSTRATED, EmotionContent.builder().memo("수정 메모").build(), null);

        // then
        assertThat(emotion.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(emotion.getMemo()).isEqualTo("수정 메모");
    }

    @Test
    void 종류가_없는_기존_감정에도_다른_종류를_설정할_수_없다() {
        // given
        Emotion emotion = 기본_한숨_빌더().state(null).memo("기존 메모").build();

        // when / then
        assertThatThrownBy(() -> emotion.update(EmotionState.ANGRY, emotion.getContent(), null))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_STATE_CHANGE_NOT_ALLOWED));
        assertThat(emotion.getState()).isNull();
    }

}
