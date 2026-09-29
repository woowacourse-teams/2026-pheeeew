package com.pheeeew.emotion.domain;

import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.만료_시각;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_ALREADY_USED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.exception.EmotionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AudioUploadTest {

    @Test
    void 최초_연결은_요청을_기록하고_객체_키를_반환한다() {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        UUID requestId = UUID.randomUUID();

        // when
        String objectKey = upload.claim(1L, requestId, 만료_시각.minusSeconds(1));

        // then
        assertThat(objectKey).isEqualTo(upload.getObjectKey());
        assertThat(upload.getClaimedRequestId()).isEqualTo(requestId);
    }

    @Test
    void 동일_연결의_재시도는_만료_후에도_최초_키를_반환한다() {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        UUID requestId = UUID.randomUUID();
        String objectKey = upload.claim(1L, requestId, 만료_시각.minusSeconds(1));

        // when / then
        assertThat(upload.claim(1L, requestId, 만료_시각.plusSeconds(1))).isEqualTo(objectKey);
        assertThat(upload.getClaimedRequestId()).isEqualTo(requestId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 다른_기기는_연결_여부와_무관하게_업로드를_찾을_수_없다(boolean claimed) {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        UUID requestId = UUID.randomUUID();
        if (claimed) {
            upload.claim(1L, requestId, 만료_시각.minusSeconds(1));
        }

        // when / then
        assertThatThrownBy(() -> upload.claim(2L, requestId, 만료_시각.plusSeconds(1)))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        assertThat(upload.getClaimedRequestId()).isEqualTo(claimed ? requestId : null);
    }

    @Test
    void 다른_감정의_요청으로_재사용할_수_없다() {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();
        UUID firstRequestId = UUID.randomUUID();
        upload.claim(1L, firstRequestId, 만료_시각.minusSeconds(1));

        // when / then
        assertThatThrownBy(() -> upload.claim(1L, UUID.randomUUID(), 만료_시각))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        assertThat(upload.getClaimedRequestId()).isEqualTo(firstRequestId);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void 미연결_업로드는_만료_시각부터_연결할_수_없다(long secondsAfterExpiry) {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();

        // when / then
        assertThatThrownBy(() -> upload.claim(1L, UUID.randomUUID(), 만료_시각.plusSeconds(secondsAfterExpiry)))
                .isInstanceOfSatisfying(EmotionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(upload.getClaimedRequestId()).isNull();
    }

    @Test
    void 연결_가능성_검사만으로는_사용_상태가_바뀌지_않는다() {
        // given
        AudioUpload upload = 기본_업로드_빌더().build();

        // when
        upload.validateClaim(1L, UUID.randomUUID(), 만료_시각.minusSeconds(1));

        // then
        assertThat(upload.getClaimedRequestId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 파일_크기는_양수여야_한다(long contentLength) {
        // given / when / then
        assertThatThrownBy(() -> 기본_업로드_빌더().contentLength(contentLength).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 객체_키와_콘텐츠_유형은_비어_있을_수_없다(String value) {
        // given / when / then
        assertThatThrownBy(() -> 기본_업로드_빌더().objectKey(value).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 기본_업로드_빌더().contentType(value).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
