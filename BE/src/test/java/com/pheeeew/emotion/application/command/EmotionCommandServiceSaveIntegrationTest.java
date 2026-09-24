package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.AudioUploadLinker;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionCommandService.class, EmotionContentResolver.class, EmotionCommandServiceSaveIntegrationTest.UploadTestConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionCommandServiceSaveIntegrationTest {

    @Autowired
    private EmotionCommandService commandService;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Device device;

    @BeforeEach
    void setUp() {
        jdbc.sql("CREATE TABLE test_audio_links (upload_id TEXT PRIMARY KEY, device_id BIGINT, request_id UUID)").update();
        device = deviceRepository.save(기본_기기_빌더().build());
    }

    @AfterEach
    void tearDown() {
        emotionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        jdbc.sql("DROP TABLE test_audio_links").update();
        jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT IF EXISTS test_emotion_insert_failure").update();
        jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT IF EXISTS test_emotion_update_failure").update();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"  메모  "})
    void 메모와_내용_없음을_선택한_위치와_각도로_저장한다(String memo) {
        // when
        Emotion saved = save(UUID.randomUUID(), memo, null);
        Emotion loaded = emotionRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.getMemo()).isEqualTo(memo == null ? null : "메모");
        assertThat(loaded.getContent().getAudio()).isNull();
        assertThat(loaded.getLongitude()).isEqualTo(126.9774);
        assertThat(loaded.getLatitude()).isEqualTo(37.5669);
        assertThat(loaded.getRotationDegrees()).isEqualTo(35.5);
        assertThat(loaded.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(loaded.getDeviceId()).isEqualTo(device.getId());
        assertThat(loaded.getNickname()).isNotBlank();
        assertThat(linkCount()).isZero();
    }

    @Test
    void 녹음_연결과_감정을_함께_확정한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        Emotion saved = save(requestId, null, "upload-id");

        // then
        assertThat(emotionRepository.findById(saved.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo("recordings/upload-id.m4a");
        assertThat(jdbc.sql("SELECT request_id FROM test_audio_links WHERE device_id = :id")
                .param("id", device.getId()).query(UUID.class).single()).isEqualTo(requestId);
    }

    @Test
    void 감정_삽입이_실패하면_녹음_연결도_취소하여_다시_사용할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        rejectRotation45();

        // when / then
        assertThatThrownBy(() -> commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 45, null, "upload-id", null, device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED);
                    assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                });
        assertThat(emotionRepository.count()).isZero();
        assertThat(linkCount()).isZero();

        save(requestId, null, "upload-id");
        assertThat(linkCount()).isOne();
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 저장_실패_후에도_호출자_트랜잭션에서_기존_감정을_조회할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        Emotion original = save(requestId, "최초 메모", null);
        rejectRotation45();

        // when / then
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                    126.9774, 37.5669, 45, null, "upload-id", null, device.getPublicId()))
                    .isInstanceOfSatisfying(EmotionException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED));
            assertThat(emotionRepository.findById(original.getId()).orElseThrow().getMemo())
                    .isEqualTo("최초 메모");
            assertThat(linkCount()).isZero();
        });
    }

    @Test
    void 없는_기기는_녹음을_연결하거나_감정을_저장할_수_없다() {
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, null, "upload-id", null, UUID.randomUUID()))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DEVICE_NOT_FOUND));
        assertThat(linkCount()).isZero();
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 필수값과_좌표가_잘못되면_녹음을_연결하지_않는다() {
        assertThatThrownBy(() -> save(null, null, "upload-id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), null,
                126.9774, 37.5669, 35.5, null, "upload-id", null, device.getPublicId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                Double.NaN, 37.5669, 35.5, null, "upload-id", null, device.getPublicId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(linkCount()).isZero();
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 수정_실패시_새_녹음_연결도_롤백하고_기존_내용을_유지한다() {
        // given
        Emotion original = save(UUID.randomUUID(), "기존 메모", null);
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT test_emotion_update_failure CHECK (state <> 'ANGRY')").update();

        // when / then
        assertThatThrownBy(() -> commandService.update(original.getId(), device.getPublicId(), EmotionState.ANGRY,
                null, "new-upload", false, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(linkCount()).isZero();
        Emotion unchanged = emotionRepository.findById(original.getId()).orElseThrow();
        assertThat(unchanged.getMemo()).isEqualTo("기존 메모");
        assertThat(unchanged.getState()).isEqualTo(EmotionState.FRUSTRATED);
        commandService.update(original.getId(), device.getPublicId(), EmotionState.EXHAUSTED,
                null, "new-upload", false, null);
        assertThat(linkCount()).isOne();
        assertThat(emotionRepository.findById(original.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo("recordings/new-upload.m4a");
        assertThat(jdbc.sql("SELECT request_id FROM test_audio_links").query(UUID.class).single())
                .isEqualTo(original.getRequestId());
    }

    private Emotion save(UUID requestId, String memo, String uploadId) {
        return commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, memo, uploadId, null, device.getPublicId());
    }

    private long linkCount() {
        return jdbc.sql("SELECT count(*) FROM test_audio_links").query(Long.class).single();
    }

    private void rejectRotation45() {
        // 녹음 연결 이후 실제 INSERT가 실패하도록 테스트 DB에만 제약을 추가한다.
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT test_emotion_insert_failure CHECK (rotation_degrees <> 45)")
                .update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UploadTestConfiguration {

        // S3 검증 대역이며, 실제 DB 쓰기로 호출자의 공동 커밋·롤백만 검증한다.
        @Bean
        AudioUploadLinker audioUploadLinker(JdbcClient jdbc) {
            return (uploadId, deviceId, requestId) -> {
                jdbc.sql("INSERT INTO test_audio_links VALUES (:uploadId, :deviceId, :requestId)")
                        .param("uploadId", uploadId).param("deviceId", deviceId).param("requestId", requestId).update();
                return "recordings/" + uploadId + ".m4a";
            };
        }
    }
}
