package com.pheeeew.emotion.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionDetailResult;
import com.pheeeew.emotion.application.dto.EmotionResult;
import com.pheeeew.emotion.application.dto.EmotionSaveResult;
import com.pheeeew.emotion.application.like.EmotionLikeService;
import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionLikeRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(EmotionLikeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionServiceDetailIntegrationTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-09-14T06:00:00Z");

    @Autowired
    private EmotionService emotionService;

    @Autowired
    private EmotionLikeService emotionLikeService;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private EmotionLikeRepository emotionLikeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean(enforceOverride = true)
    private Clock clock;

    private Device device;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(CURRENT_TIME);
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        device = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.save(기본_한숨_빌더().memo("오늘은 힘들었다").build());
        updateCreatedAt(CURRENT_TIME.minusSeconds(60));
    }

    @AfterEach
    void tearDown() {
        emotionLikeRepository.deleteAll();
        emotionRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void 한숨_내용과_전체_좋아요_수는_유지하고_조회한_기기의_좋아요_여부를_반환한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Device unlikedDevice = deviceRepository.save(기본_기기_빌더().build());
        Emotion anotherEmotion = emotionRepository.save(기본_한숨_빌더().build());
        emotionLikeService.update(emotion.getId(), device.getPublicId(), true);
        emotionLikeService.update(emotion.getId(), anotherDevice.getPublicId(), true);
        emotionLikeService.update(anotherEmotion.getId(), unlikedDevice.getPublicId(), true);
        EmotionResult expectedEmotion = EmotionResult.from(emotionRepository.findById(emotion.getId()).orElseThrow());

        // when
        EmotionDetailResult liked = emotionService.findById(emotion.getId(), device.getPublicId());
        EmotionDetailResult unliked = emotionService.findById(emotion.getId(), unlikedDevice.getPublicId());

        // then
        assertThat(liked.emotion()).isEqualTo(expectedEmotion);
        assertThat(liked.emotion().memo()).isEqualTo("오늘은 힘들었다");
        assertThat(unliked.emotion()).isEqualTo(expectedEmotion);
        assertThat(liked.like()).isEqualTo(EmotionLikeResult.of(true, 2));
        assertThat(unliked.like()).isEqualTo(EmotionLikeResult.of(false, 2));
    }

    @Test
    void 좋아요가_없으면_false와_0을_반환한다() {
        // when
        EmotionDetailResult result = emotionService.findById(emotion.getId(), device.getPublicId());

        // then
        assertThat(result.like()).isEqualTo(EmotionLikeResult.of(false, 0));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T14:59:59.999999Z, 2026-08-31T15:00:00Z",
            "2026-09-14T15:00:00Z, 2026-09-01T15:00:00Z",
            "2026-09-14T06:00:00Z, 2026-09-14T06:00:01Z"
    })
    void 조회_시작_경계와_그_이후에_등록된_한숨은_상세를_조회할_수_있다(String queryTime, String createdTime) {
        // given
        given(clock.instant()).willReturn(Instant.parse(queryTime));
        updateCreatedAt(Instant.parse(createdTime));

        // when
        EmotionDetailResult result = emotionService.findById(emotion.getId(), device.getPublicId());

        // then
        assertThat(result.emotion().id()).isEqualTo(emotion.getId());
        assertThat(result.emotion().createdAt()).isEqualTo(Instant.parse(createdTime));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T14:59:59.999999Z, 2026-08-31T14:59:59.999999Z",
            "2026-09-14T15:00:00Z, 2026-09-01T14:59:59.999999Z"
    })
    void 조회_시작_경계보다_이전에_등록된_한숨은_만료로_거부한다(String queryTime, String createdTime) {
        // given
        given(clock.instant()).willReturn(Instant.parse(queryTime));
        updateCreatedAt(Instant.parse(createdTime));

        // when / then
        assertThatThrownBy(() -> emotionService.findById(emotion.getId(), device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_EXPIRED));
    }

    @Test
    void 한국_시간_자정이_지나면_이미_조회했던_한숨도_데이터_삭제_없이_만료된다() {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T14:59:59.999999Z"));
        Instant createdAt = Instant.parse("2026-08-31T15:00:00Z");
        updateCreatedAt(createdAt);
        EmotionDetailResult beforeMidnight = emotionService.findById(emotion.getId(), device.getPublicId());
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T15:00:00Z"));

        // when / then
        assertThat(beforeMidnight.emotion().id()).isEqualTo(emotion.getId());
        assertThatThrownBy(() -> emotionService.findById(emotion.getId(), device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_EXPIRED));
        Emotion savedEmotion = emotionRepository.findById(emotion.getId()).orElseThrow();
        assertThat(savedEmotion.getCreatedAt()).isEqualTo(createdAt);
        assertThat(savedEmotion.getDeletedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없거나_삭제된_한숨은_기간_만료_여부와_관계없이_조회할_수_없다(boolean expired) {
        // given
        if (expired) {
            updateCreatedAt(CURRENT_TIME.minus(14, ChronoUnit.DAYS));
        }
        emotionLikeService.update(emotion.getId(), device.getPublicId(), true);
        Emotion deletedEmotion = emotionRepository.findById(emotion.getId()).orElseThrow();
        deletedEmotion.delete();
        emotionRepository.saveAndFlush(deletedEmotion);

        // when / then
        assertEmotionNotFound(Long.MAX_VALUE, device.getPublicId());
        assertEmotionNotFound(emotion.getId(), device.getPublicId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 등록되지_않은_기기는_한숨의_기간_만료_여부와_관계없이_거부한다(boolean expired) {
        // given
        if (expired) {
            updateCreatedAt(CURRENT_TIME.minus(14, ChronoUnit.DAYS));
        }
        UUID unknownDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> emotionService.findById(emotion.getId(), unknownDevicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void 등록_재요청은_삭제와_기간_만료_여부와_관계없이_최초_내용과_기기별_좋아요_정보를_조회한다(boolean deleted, boolean expired) {
        // given
        if (expired) {
            updateCreatedAt(CURRENT_TIME.minus(14, ChronoUnit.DAYS));
        }
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        emotionLikeService.update(emotion.getId(), device.getPublicId(), true);
        Emotion savedEmotion = emotionRepository.findById(emotion.getId()).orElseThrow();
        if (deleted) {
            savedEmotion.delete();
            emotionRepository.saveAndFlush(savedEmotion);
        }
        EmotionResult expectedEmotion = EmotionResult.from(savedEmotion);

        // when
        EmotionSaveResult liked = emotionService.save(emotion.getRequestId(), 129.0756, 35.1796, "변경한 메모", device.getPublicId());
        EmotionSaveResult unliked = emotionService.save(emotion.getRequestId(), 129.0756, 35.1796, "변경한 메모", anotherDevice.getPublicId());

        // then
        assertThat(liked.created()).isFalse();
        assertThat(unliked.created()).isFalse();
        assertThat(liked.emotion()).isEqualTo(expectedEmotion);
        assertThat(unliked.emotion()).isEqualTo(expectedEmotion);
        assertThat(liked.like()).isEqualTo(EmotionLikeResult.of(true, 1));
        assertThat(unliked.like()).isEqualTo(EmotionLikeResult.of(false, 1));
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 등록_재요청의_좋아요_조회에서도_등록되지_않은_기기는_거부한다() {
        // given
        UUID unknownDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> emotionService.save(emotion.getRequestId(), 129.0756, 35.1796, null, unknownDevicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    void 새로운_한숨의_저장_결과는_초기_좋아요_정보를_포함한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        EmotionSaveResult result = emotionService.save(requestId, 126.9780, 37.5664, null, device.getPublicId());

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.like()).isEqualTo(EmotionLikeResult.of(false, 0));
        assertThat(emotionRepository.findById(result.emotion().id()).orElseThrow().getLikeCount()).isZero();
    }

    private void updateCreatedAt(Instant createdAt) {
        jdbcClient.sql("UPDATE sighs SET created_at = CAST(:createdAt AS TIMESTAMPTZ) WHERE id = :id")
                .param("createdAt", createdAt.toString())
                .param("id", emotion.getId())
                .update();
    }

    private void assertEmotionNotFound(Long emotionId, UUID devicePublicId) {
        assertThatThrownBy(() -> emotionService.findById(emotionId, devicePublicId))
                .hasMessage("한숨을 찾을 수 없습니다.")
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
    }
}
