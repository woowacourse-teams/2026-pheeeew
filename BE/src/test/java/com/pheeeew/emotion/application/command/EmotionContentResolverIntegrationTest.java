package com.pheeeew.emotion.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.emotion.application.AudioUploadLinker;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@PostgisDataJpaTest
@Import(EmotionContentResolver.class)
class EmotionContentResolverIntegrationTest {

    private static final Long DEVICE_ID = 1L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private EmotionContentResolver resolver;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private AudioUploadLinker linker;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "  오늘은 지쳤다  "})
    void 메모와_내용_없음은_업로드_기능을_호출하지_않는다(String memo) {
        // when
        EmotionContent content = resolver.resolve(memo, null, DEVICE_ID, REQUEST_ID);

        // then
        String expected = memo == null || memo.isBlank() ? null : memo.strip();
        assertThat(content.getMemo()).isEqualTo(expected);
        assertThat(content.getAudio()).isNull();
        verifyNoInteractions(linker);
    }

    @Test
    void 현재_트랜잭션에서_업로드를_연결하고_검증된_키로만_Audio를_만든다() {
        // given
        Object currentEntityManager = TransactionSynchronizationManager.getResource(entityManagerFactory);
        assertThat(currentEntityManager).isNotNull();
        when(linker.claim("upload-id", DEVICE_ID, REQUEST_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(currentEntityManager);
            return "recordings/server-issued-key.m4a";
        });

        // when
        EmotionContent content = resolver.resolve(null, "upload-id", DEVICE_ID, REQUEST_ID);

        // then
        assertThat(content.getMemo()).isNull();
        assertThat(content.getAudio().getObjectKey()).isEqualTo("recordings/server-issued-key.m4a");
        verify(linker).claim("upload-id", DEVICE_ID, REQUEST_ID);
    }

    @ParameterizedTest
    @EnumSource(value = EmotionErrorCode.class, names = {
            "EMOTION_AUDIO_UPLOAD_NOT_FOUND", "EMOTION_AUDIO_UPLOAD_NOT_READY",
            "EMOTION_AUDIO_UPLOAD_ALREADY_USED", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"
    })
    void 업로드_확인_실패를_내용_없음으로_바꾸지_않고_전달한다(EmotionErrorCode errorCode) {
        // given
        EmotionException failure = new EmotionException(errorCode);
        when(linker.claim("upload-id", DEVICE_ID, REQUEST_ID)).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> resolver.resolve(null, "upload-id", DEVICE_ID, REQUEST_ID))
                .isSameAs(failure);
    }

    @Test
    void 잘못된_내용은_업로드를_사용_처리하기_전에_거부한다() {
        assertThatThrownBy(() -> resolver.resolve("메모", "upload-id", DEVICE_ID, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve("가".repeat(201), "upload-id", DEVICE_ID, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(null, "  ", DEVICE_ID, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(linker);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 감정_저장_트랜잭션_없이_업로드를_사용_처리하지_않는다() {
        assertThatThrownBy(() -> resolver.resolve(null, "upload-id", DEVICE_ID, REQUEST_ID))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(linker);
    }
}
