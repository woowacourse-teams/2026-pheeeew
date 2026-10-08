package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NICKNAME_REQUIRED;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.AudioUploadFixture.기본_업로드_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_ALREADY_USED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_LOCATION_OUT_OF_SERVICE_AREA;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NOT_FOUND;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.infra.S3ObjectVerifier;
import com.pheeeew.emotion.domain.AudioUpload;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.AudioUploadRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionCommandService.class, EmotionContentResolver.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionCommandServiceSaveIntegrationTest {

    @Autowired
    private EmotionCommandService commandService;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AudioUploadRepository uploads;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private S3ObjectVerifier objectVerifier;

    private Device device;
    private AudioUpload upload;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        device = deviceRepository.save(기본_기기_빌더().build());
        upload = uploads.save(기본_업로드_빌더().deviceId(device.getId())
                .expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    @AfterEach
    void tearDown() {
        emotionRepository.deleteAllInBatch();
        uploads.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
        jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT IF EXISTS test_emotion_insert_failure").update();
        jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT IF EXISTS test_emotion_update_failure").update();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
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
        assertThat(jdbc.sql("SELECT nickname FROM emotions WHERE id = :id")
                .param("id", loaded.getId()).query(String.class).single()).isEqualTo("익명");
        assertThat(loaded.isAnonymous()).isTrue();
        assertThat(loaded.getRegionCode()).isEqualTo("11010530");
        assertThat(loaded.getRegionClassifiedAt()).isNotNull();
        assertThat(linkCount()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void 익명_선택을_저장하고_과거_닉네임_컬럼에는_DB_기본값을_적용한다(Boolean anonymous) {
        // given
        deviceService.updateNickname(device.getPublicId(), "스타크");

        // when
        Emotion saved = commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, "메모", null, null, device.getPublicId(), anonymous);
        Emotion loaded = emotionRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.isAnonymous()).isEqualTo(!Boolean.FALSE.equals(anonymous));
        assertThat(loaded.getDeviceId()).isEqualTo(device.getId());
        assertThat(jdbc.sql("SELECT nickname FROM emotions WHERE id = :id")
                .param("id", loaded.getId()).query(String.class).single()).isEqualTo("익명");
    }

    @Test
    void 닉네임_없는_기명_등록은_녹음을_연결하지_않고_설정_후_같은_요청으로_등록할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, null, upload.getUploadId(), null, device.getPublicId(), false))
                .isInstanceOfSatisfying(DeviceException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(DEVICE_NICKNAME_REQUIRED));
        assertThat(emotionRepository.count()).isZero();
        assertThat(linkCount()).isZero();
        verifyNoInteractions(objectVerifier);

        deviceService.updateNickname(device.getPublicId(), "스타크");
        Emotion saved = commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, null, upload.getUploadId(), null, device.getPublicId(), false);
        assertThat(emotionRepository.findById(saved.getId()).orElseThrow().isAnonymous()).isFalse();
        assertThat(emotionRepository.count()).isOne();
        assertThat(linkCount()).isOne();
    }

    @Test
    void 닉네임_없는_기기의_기명_재시도도_최초_익명_감정을_반환한다() {
        // given
        UUID requestId = UUID.randomUUID();
        Emotion first = save(requestId, "최초 메모", null);

        // when
        Emotion retried = commandService.save(requestId, EmotionState.ANGRY,
                0, 0, 0, null, upload.getUploadId(), UUID.randomUUID(), device.getPublicId(), false);

        // then
        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(retried.isAnonymous()).isTrue();
        assertThat(retried.getMemo()).isEqualTo("최초 메모");
        assertThat(emotionRepository.count()).isOne();
        assertThat(linkCount()).isZero();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void 내용_필수_버전의_유효한_재시도도_기존_NONE_감정을_덮어쓰지_않는다(Boolean anonymous) {
        // given: V1의 내용 없는 감정을 V2(null) 또는 V3의 메모 본문으로 재시도한다.
        UUID requestId = UUID.randomUUID();
        Emotion first = save(requestId, null, null);

        // when
        Emotion retried = commandService.save(requestId, EmotionState.ANGRY,
                126.9774, 37.5669, 90, "새 메모", null, null, device.getPublicId(), anonymous);

        // then
        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(retried.isAnonymous()).isTrue();
        assertThat(retried.getMemo()).isNull();
        assertThat(retried.getContent().hasAudio()).isFalse();
        assertThat(retried.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(emotionRepository.count()).isOne();
        assertThat(linkCount()).isZero();
        verifyNoInteractions(objectVerifier);
    }

    @Test
    void 녹음_연결과_감정을_함께_확정한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        Emotion saved = save(requestId, null, upload.getUploadId());

        // then
        Emotion loaded = emotionRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRegionCode()).isEqualTo("11010530");
        assertThat(loaded.getRegionClassifiedAt()).isNotNull();
        assertThat(loaded.getContent().getAudio().getObjectKey())
                .isEqualTo(upload.getObjectKey());
        assertThat(uploads.findByUploadId(upload.getUploadId()).orElseThrow().getClaimedRequestId())
                .isEqualTo(requestId);
    }

    @Test
    void 감정_삽입이_실패하면_녹음_연결도_취소하여_다시_사용할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        rejectRotation45();

        // when / then
        assertThatThrownBy(() -> commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 45, null, upload.getUploadId(), null, device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(EMOTION_SAVE_FAILED);
                    assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                });
        assertThat(emotionRepository.count()).isZero();
        assertThat(linkCount()).isZero();

        save(requestId, null, upload.getUploadId());
        assertThat(linkCount()).isOne();
        assertThat(emotionRepository.count()).isOne();
    }

    @ParameterizedTest
    @CsvSource({"0,0,NONE", "0,0,MEMO", "0,0,AUDIO", "128.02,38,NONE", "128.02,38,MEMO", "128.02,38,AUDIO"})
    void 지원_범위_밖의_신규_등록은_모든_콘텐츠를_거부하고_녹음_연결을_시작하지_않는다(
            double longitude, double latitude, String contentType
    ) {
        // given
        UUID requestId = UUID.randomUUID();
        String memo = contentType.equals("MEMO") ? "메모" : null;
        String uploadId = contentType.equals("AUDIO") ? upload.getUploadId() : null;

        // when / then
        assertThatThrownBy(() -> commandService.save(requestId, EmotionState.FRUSTRATED,
                longitude, latitude, 35.5, memo, uploadId, null, device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_LOCATION_OUT_OF_SERVICE_AREA));
        assertThat(emotionRepository.count()).isZero();
        assertThat(linkCount()).isZero();
        verifyNoInteractions(objectVerifier);

        // 같은 요청 식별자와 업로드로 허용 위치를 선택하면 등록할 수 있다.
        Emotion saved = save(requestId, memo, uploadId);
        assertThat(saved.getRegionCode()).isEqualTo("11010530");
        assertThat(emotionRepository.count()).isOne();
        assertThat(linkCount()).isEqualTo(contentType.equals("AUDIO") ? 1 : 0);
    }

    @Test
    void 경계_밖_1km_이내의_신규_녹음은_원래_좌표로_지역에_연결한다() {
        // given / when
        Emotion saved = commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                128.005, 38, 35.5, null, upload.getUploadId(), null, device.getPublicId());
        Emotion loaded = emotionRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.getRegionCode()).isEqualTo("11010530");
        assertThat(loaded.getRegionClassifiedAt()).isNotNull();
        assertThat(loaded.getLongitude()).isEqualTo(128.005);
        assertThat(loaded.getLatitude()).isEqualTo(38);
        assertThat(loaded.getContent().getAudio().getObjectKey()).isEqualTo(upload.getObjectKey());
        assertThat(linkCount()).isOne();
    }

    @Test
    void 지원_범위_밖이어도_기존_그룹_권한_확인을_먼저_유지한다() {
        // given / when / then
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                0, 0, 35.5, null, upload.getUploadId(), UUID.randomUUID(), device.getPublicId()))
                .isInstanceOfSatisfying(GroupException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(GROUP_NOT_FOUND));
        assertThat(emotionRepository.count()).isZero();
        assertThat(linkCount()).isZero();
        verifyNoInteractions(objectVerifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unverified", "sqlFailure"})
    void 지역_분류가_실패하면_녹음_연결을_시작하지_않고_복구_후_재사용한다(String failure) {
        // given: 실제 분류 경로에서 오류를 발생시킨다.
        UUID requestId = UUID.randomUUID();
        boolean unavailableTable = failure.equals("sqlFailure");
        if (unavailableTable) {
            jdbc.sql("ALTER TABLE regions RENAME TO unavailable_regions").update();
        } else {
            jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        }

        // when / then
        try {
            var assertion = assertThatThrownBy(() -> save(requestId, null, upload.getUploadId()));
            if (unavailableTable) {
                assertion.isInstanceOf(DataAccessException.class);
            } else {
                assertion.isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
            }
            assertThat(emotionRepository.count()).isZero();
            assertThat(linkCount()).isZero();
            verifyNoInteractions(objectVerifier);
        } finally {
            if (unavailableTable) {
                jdbc.sql("ALTER TABLE unavailable_regions RENAME TO regions").update();
            }
        }
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        Emotion saved = save(requestId, null, upload.getUploadId());
        assertThat(emotionRepository.findById(saved.getId()).orElseThrow().getRegionCode())
                .isEqualTo("11010530");
        assertThat(emotionRepository.count()).isOne();
        assertThat(linkCount()).isOne();
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
                    126.9774, 37.5669, 45, null, upload.getUploadId(), null, device.getPublicId()))
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
                126.9774, 37.5669, 35.5, null, upload.getUploadId(), null, UUID.randomUUID()))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DEVICE_NOT_FOUND));
        assertThat(linkCount()).isZero();
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 필수값과_좌표가_잘못되면_녹음을_연결하지_않는다() {
        assertThatThrownBy(() -> save(null, null, upload.getUploadId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), null,
                126.9774, 37.5669, 35.5, null, upload.getUploadId(), null, device.getPublicId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commandService.save(UUID.randomUUID(), EmotionState.FRUSTRATED,
                Double.NaN, 37.5669, 35.5, null, upload.getUploadId(), null, device.getPublicId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(linkCount()).isZero();
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 수정_실패시_새_녹음_연결도_롤백하고_기존_내용을_유지한다() {
        // given
        Emotion original = save(UUID.randomUUID(), "기존 메모", null);
        Emotion stored = emotionRepository.findById(original.getId()).orElseThrow();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT test_emotion_update_failure CHECK (state <> 'ANGRY')").update();

        // when / then
        assertThatThrownBy(() -> commandService.update(original.getId(), device.getPublicId(), EmotionState.ANGRY,
                null, upload.getUploadId(), false, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(linkCount()).isZero();
        Emotion unchanged = emotionRepository.findById(original.getId()).orElseThrow();
        assertThat(unchanged.getMemo()).isEqualTo("기존 메모");
        assertThat(unchanged.getState()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(unchanged.getRegionCode()).isEqualTo(stored.getRegionCode());
        assertThat(unchanged.getRegionClassifiedAt()).isEqualTo(stored.getRegionClassifiedAt());
        commandService.update(original.getId(), device.getPublicId(), EmotionState.EXHAUSTED,
                null, upload.getUploadId(), false, null);
        assertThat(linkCount()).isOne();
        Emotion updated = emotionRepository.findById(original.getId()).orElseThrow();
        assertThat(updated.getRegionCode()).isEqualTo(stored.getRegionCode());
        assertThat(updated.getRegionClassifiedAt()).isEqualTo(stored.getRegionClassifiedAt());
        assertThat(emotionRepository.findById(original.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo(upload.getObjectKey());
        assertThat(uploads.findByUploadId(upload.getUploadId()).orElseThrow().getClaimedRequestId())
                .isEqualTo(original.getRequestId());
    }

    @Test
    void 교체한_이전_녹음도_다른_감정에_재사용할_수_없다() {
        // given
        UUID requestId = UUID.randomUUID();
        Emotion original = save(requestId, null, upload.getUploadId());
        AudioUpload replacement = uploads.save(기본_업로드_빌더().deviceId(device.getId())
                .objectKey("recordings/replacement.m4a").expiresAt(Instant.now().plusSeconds(3600)).build());

        // when
        commandService.update(original.getId(), device.getPublicId(), EmotionState.ANGRY,
                null, replacement.getUploadId(), false, null);

        // then
        assertThat(emotionRepository.findById(original.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo(replacement.getObjectKey());
        assertThat(uploads.findAll()).allSatisfy(value -> assertThat(value.getClaimedRequestId()).isEqualTo(requestId));
        assertThatThrownBy(() -> save(UUID.randomUUID(), null, upload.getUploadId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_UPLOAD_ALREADY_USED));
        assertThat(emotionRepository.count()).isOne();
    }

    private Emotion save(UUID requestId, String memo, String uploadId) {
        return commandService.save(requestId, EmotionState.FRUSTRATED,
                126.9774, 37.5669, 35.5, memo, uploadId, null, device.getPublicId());
    }

    private long linkCount() {
        return jdbc.sql("SELECT count(*) FROM audio_uploads WHERE claimed_request_id IS NOT NULL").query(Long.class).single();
    }

    private void rejectRotation45() {
        // 녹음 연결 이후 실제 INSERT가 실패하도록 테스트 DB에만 제약을 추가한다.
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT test_emotion_insert_failure CHECK (rotation_degrees <> 45)")
                .update();
    }

}
