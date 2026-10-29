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
import com.pheeeew.emotion.application.command.EmotionContentResolver.PreparedContent;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import(EmotionContentResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private S3ObjectVerifier objectVerifier;

    @AfterEach
    void tearDown() {
        uploads.deleteAllInBatch();
        devices.deleteAllInBatch();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "  오늘은 지쳤다  "})
    void 메모와_내용_없음은_업로드_기능을_호출하지_않는다(String memo) {
        // when
        EmotionContent content = resolve(memo, null, 1L, REQUEST_ID);

        // then
        String expected = memo == null || memo.isBlank() ? null : memo.strip();
        assertThat(content.getMemo()).isEqualTo(expected);
        assertThat(content.getAudio()).isNull();
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void S3_확인에는_외부_트랜잭션을_중단하고_복귀한_저장_트랜잭션에서만_연결한다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isNull();
            assertThat(current(upload).getClaimedRequestId()).isNull();
            return null;
        }).when(objectVerifier).verify(any());

        // when
        EmotionContent content = new TransactionTemplate(transactions).execute(status -> {
            Object currentEntityManager = TransactionSynchronizationManager.getResource(entityManagerFactory);
            assertThat(currentEntityManager).isNotNull();
            PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(currentEntityManager);
            return resolver.resolve(prepared, upload.getDeviceId(), REQUEST_ID);
        });

        // then
        assertThat(content.getMemo()).isNull();
        assertThat(content.getAudio().getObjectKey()).isEqualTo(upload.getObjectKey());
        assertThat(current(upload).getClaimedRequestId()).isEqualTo(REQUEST_ID);
        verify(objectVerifier).verify(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 이력이_없거나_소유자가_다르면_S3_확인_전에_같은_오류로_거부한다(boolean missing) {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        String uploadId = missing ? UUID.randomUUID().toString() : upload.getUploadId();
        Long deviceId = missing ? upload.getDeviceId() : devices.save(기본_기기_빌더().build()).getId();

        // when / then
        assertThatThrownBy(() -> resolve(null, uploadId, deviceId, REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        assertThat(current(upload).getClaimedRequestId()).isNull();
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
            uploads.saveAndFlush(upload);
        }

        // when / then
        assertThatThrownBy(() -> resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(expired ? EMOTION_AUDIO_UPLOAD_NOT_READY : EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        assertThat(current(upload).getClaimedRequestId()).isEqualTo(expired ? null : previousRequestId);
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 같은_요청에_연결된_업로드는_만료_후에도_S3_확인_없이_기존_키를_반환한다() {
        // given
        Instant expiresAt = Instant.now().minusSeconds(1);
        AudioUpload upload = saveUpload(expiresAt);
        upload.claim(upload.getDeviceId(), REQUEST_ID, expiresAt.minusSeconds(1));
        uploads.saveAndFlush(upload);

        // when
        EmotionContent content = resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);

        // then
        assertThat(content.getAudio().getObjectKey()).isEqualTo(upload.getObjectKey());
        assertThat(current(upload).getClaimedRequestId()).isEqualTo(REQUEST_ID);
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
        assertThatThrownBy(() -> resolve(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID))
                .isSameAs(failure);
        assertThat(current(upload).getClaimedRequestId()).isNull();
    }

    @Test
    void 사전_확인_후_다른_요청이_연결한_업로드는_재검증에서_거부한다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
        UUID otherRequestId = UUID.randomUUID();
        upload.claim(upload.getDeviceId(), otherRequestId, Instant.now());
        uploads.saveAndFlush(upload);

        // when / then
        assertThatThrownBy(() -> resolve(prepared, upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        assertThat(current(upload).getClaimedRequestId()).isEqualTo(otherRequestId);
    }

    @Test
    void 외부_트랜잭션이_사전_조회한_업로드도_다른_트랜잭션의_연결을_재검증한다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        UUID otherRequestId = UUID.randomUUID();
        TransactionTemplate concurrent = new TransactionTemplate(transactions);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
            concurrent.executeWithoutResult(other -> {
                AudioUpload claimed = uploads.findByUploadIdForUpdate(upload.getUploadId()).orElseThrow();
                claimed.claim(upload.getDeviceId(), otherRequestId, Instant.now());
            });

            // then: 사전 조회한 엔티티가 영속성 컨텍스트에 남아 있어도 현재 DB 연결 상태를 검사한다.
            assertThatThrownBy(() -> resolver.resolve(prepared, upload.getDeviceId(), REQUEST_ID))
                    .isInstanceOfSatisfying(EmotionException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_ALREADY_USED));
            status.setRollbackOnly();
        });
        assertThat(current(upload).getClaimedRequestId()).isEqualTo(otherRequestId);
    }

    @Test
    void 사전_확인_후_만료된_업로드는_연결하지_않는다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
        jdbc.sql("UPDATE audio_uploads SET expires_at = :at WHERE id = :id")
                .param("at", Timestamp.from(Instant.now().minusSeconds(1))).param("id", upload.getId()).update();

        // when / then
        assertThatThrownBy(() -> resolve(prepared, upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(current(upload).getClaimedRequestId()).isNull();
    }

    @Test
    void 사전_확인한_메타데이터가_바뀌면_연결하지_않는다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
        jdbc.sql("UPDATE audio_uploads SET content_length = content_length + 1 WHERE id = :id")
                .param("id", upload.getId()).update();

        // when / then
        assertThatThrownBy(() -> resolve(prepared, upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(current(upload).getClaimedRequestId()).isNull();
    }

    @Test
    void 기존_연결이라_S3를_생략한_뒤_연결이_사라지면_미검증_업로드를_승인하지_않는다() {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        upload.claim(upload.getDeviceId(), REQUEST_ID, Instant.now());
        uploads.saveAndFlush(upload);
        PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
        jdbc.sql("UPDATE audio_uploads SET claimed_request_id = NULL WHERE id = :id")
                .param("id", upload.getId()).update();

        // when / then
        assertThatThrownBy(() -> resolve(prepared, upload.getDeviceId(), REQUEST_ID))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_READY));
        assertThat(current(upload).getClaimedRequestId()).isNull();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 사전_확인_결과는_다른_기기나_요청에_사용하지_못한다(boolean differentDevice) {
        // given
        AudioUpload upload = saveUpload(Instant.now().plusSeconds(3600));
        PreparedContent prepared = resolver.prepare(null, upload.getUploadId(), upload.getDeviceId(), REQUEST_ID);
        Long deviceId = differentDevice ? upload.getDeviceId() + 1 : upload.getDeviceId();
        UUID requestId = differentDevice ? REQUEST_ID : UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> resolve(prepared, deviceId, requestId))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_NOT_FOUND));
        assertThat(current(upload).getClaimedRequestId()).isNull();
    }

    @Test
    void 잘못된_내용은_업로드를_사용_처리하기_전에_거부한다() {
        assertThatThrownBy(() -> resolve("메모", "upload-id", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolve("가".repeat(201), "upload-id", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolve(null, "  ", 1L, REQUEST_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 감정_저장_트랜잭션_없이_업로드를_사용_처리하지_않는다() {
        // given
        PreparedContent prepared = resolver.prepare("메모", null, 1L, REQUEST_ID);

        // when / then
        assertThatThrownBy(() -> resolver.resolve(prepared, 1L, REQUEST_ID))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(objectVerifier);
    }

    private EmotionContent resolve(String memo, String uploadId, Long deviceId, UUID requestId) {
        PreparedContent prepared = resolver.prepare(memo, uploadId, deviceId, requestId);
        return resolve(prepared, deviceId, requestId);
    }

    private EmotionContent resolve(PreparedContent prepared, Long deviceId, UUID requestId) {
        return new TransactionTemplate(transactions).execute(status -> resolver.resolve(prepared, deviceId, requestId));
    }

    private AudioUpload current(AudioUpload upload) {
        return uploads.findById(upload.getId()).orElseThrow();
    }

    private AudioUpload saveUpload(Instant expiresAt) {
        Long deviceId = devices.save(기본_기기_빌더().build()).getId();
        return uploads.saveAndFlush(기본_업로드_빌더().deviceId(deviceId).expiresAt(expiresAt).build());
    }
}
