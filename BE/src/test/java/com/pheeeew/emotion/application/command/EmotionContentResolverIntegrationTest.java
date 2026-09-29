package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_ALREADY_USED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_NOT_READY;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
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

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private EmotionContentResolver resolver;
    @Autowired
    private AudioUploadRepository uploads;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @MockitoBean
    private S3ObjectVerifier objectVerifier;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "  오늘은 지쳤다  "})
    void 메모와_내용_없음은_업로드_기능을_호출하지_않는다(String memo) {
        // when
        EmotionContent content = resolver.resolve(memo, null, 1L, REQUEST_ID);

        // then
        String expected = memo == null || memo.isBlank() ? null : memo.strip();
        assertThat(content.getMemo()).isEqualTo(expected);
        assertThat(content.getAudio()).isNull();
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 현재_트랜잭션에서_객체_검증_후_업로드를_연결한다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        Object currentEntityManager = TransactionSynchronizationManager.getResource(entityManagerFactory);
        assertThat(currentEntityManager).isNotNull();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(currentEntityManager);
            assertThat(upload.getClaimedRequestId()).isNull();
            return null;
        }).when(objectVerifier).verify(upload);

        // when
        EmotionContent content = resolver.resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);

        // then
        assertThat(content.getMemo()).isNull();
        assertThat(content.getAudio().getObjectKey()).isEqualTo(upload.getObjectKey());
        assertThat(upload.getClaimedRequestId()).isEqualTo(REQUEST_ID);
        verify(objectVerifier).verify(upload);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 이력이_없거나_소유자가_다르면_S3_확인_전에_같은_오류로_거부한다(boolean missing) {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        String uploadId = missing ? UUID.randomUUID().toString() : upload.getUploadId();
        Long deviceId = missing ? upload.getDeviceId() : devices.save(기본_기기_빌더().build()).getId();

        // when / then
        assertThatThrownBy(() -> resolver.resolve(null, uploadId, deviceId, REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        assertThat(upload.getClaimedRequestId()).isNull();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 만료되거나_다른_요청에_연결된_업로드는_S3_확인_전에_거부한다(boolean expired) {
        // given
        Instant expiresAt = Instant.now().minusSeconds(1);
        AudioUpload upload = saveUpload(expiresAt);
        UUID previousRequestId = UUID.randomUUID();
        if (!expired) {
            upload.claim(upload.getDeviceId(), previousRequestId, expiresAt.minusSeconds(1));
        }

        // when / then
        assertThatThrownBy(() -> resolver.resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(expired ? EMOTION_AUDIO_UPLOAD_NOT_READY : EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        assertThat(upload.getClaimedRequestId()).isEqualTo(expired ? null : previousRequestId);
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 같은_요청에_연결된_업로드는_만료_후에도_S3_확인_없이_기존_키를_반환한다() {
        // given
        Instant expiresAt = Instant.now().minusSeconds(1);
        AudioUpload upload = saveUpload(expiresAt);
        upload.claim(upload.getDeviceId(), REQUEST_ID, expiresAt.minusSeconds(1));

        // when
        EmotionContent content = resolver.resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);

        // then
        assertThat(content.getAudio().getObjectKey()).isEqualTo(upload.getObjectKey());
        assertThat(upload.getClaimedRequestId()).isEqualTo(REQUEST_ID);
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @EnumSource(value = EmotionErrorCode.class, names = {
            "EMOTION_AUDIO_UPLOAD_NOT_READY", "EMOTION_AUDIO_UPLOAD_UNAVAILABLE"
    })
    void S3_확인_실패를_그대로_전달하고_연결하지_않는다(EmotionErrorCode errorCode) {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        EmotionException failure = new EmotionException(errorCode, new IllegalStateException("storage failure"));
        doThrow(failure).when(objectVerifier).verify(any());

        // when / then
        assertThatThrownBy(() -> resolver.resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID))
                .isSameAs(failure);
        assertThat(upload.getClaimedRequestId()).isNull();
    }

    @Test
    void 잘못된_내용은_업로드를_사용_처리하기_전에_거부한다() {
        assertThatThrownBy(() -> resolver.resolve("메모", "upload-id", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve("가".repeat(201), "upload-id", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(null, "  ", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(objectVerifier);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 감정_저장_트랜잭션_없이_업로드를_사용_처리하지_않는다() {
        assertThatThrownBy(() -> resolver.resolve(null, "upload-id", 1L, REQUEST_ID))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(objectVerifier);
    }

    private AudioUpload saveUpload(Instant expiresAt) {
        Long deviceId = devices.save(기본_기기_빌더().build()).getId();
        return uploads.saveAndFlush(기본_업로드_빌더().deviceId(deviceId).expiresAt(expiresAt).build());
    }
}
