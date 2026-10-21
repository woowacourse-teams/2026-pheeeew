package com.pheeeew.emotion.application.command;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@PostgisDataJpaTest
@Import(EmotionContentResolver.class)
class EmotionContentResolverWithoutUploadIntegrationTest {

    @Autowired
    private EmotionContentResolver resolver;

    @Test
    void 업로드_구현체가_없어도_메모는_처리한다() {
        // when
        UUID requestId = UUID.randomUUID();
        var prepared = resolver.prepare("  메모  ", null, 1L, requestId);
        EmotionContent content = resolver.resolve(prepared, 1L, requestId);

        // then
        assertThat(content.getMemo()).isEqualTo("메모");
        assertThat(content.getAudio()).isNull();
    }

    @Test
    void 업로드_구현체가_없으면_녹음을_승인하지_않는다() {
        UUID requestId = UUID.randomUUID();
        var prepared = resolver.prepare(null, "upload-id", 1L, requestId);
        assertThatThrownBy(() -> resolver.resolve(prepared, 1L, requestId))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_UNAVAILABLE));
    }
}
