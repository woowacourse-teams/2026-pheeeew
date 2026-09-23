package com.pheeeew.emotion.application.like;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.emotion.fixture.EmotionLikeFixture.기본_좋아요_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionLike;
import com.pheeeew.emotion.domain.repository.EmotionLikeRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import(EmotionLikeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionLikeServiceIntegrationTest {

    @Autowired
    private EmotionLikeService emotionLikeService;

    @Autowired
    private EmotionLikeRepository emotionLikeRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcClient jdbcClient;

    private Device device;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.save(기본_한숨_빌더().build());
    }

    @AfterEach
    void tearDown() {
        emotionLikeRepository.deleteAllInBatch();
        emotionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 요청한_좋아요_상태를_반영하고_같은_상태를_반복_요청해도_유지한다() {
        // given
        Long emotionId = emotion.getId();
        UUID devicePublicId = device.getPublicId();

        // when / then
        assertThat(emotionLikeService.update(emotionId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(emotionId, 0);
        assertThat(emotionLikeService.update(emotionId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertLikeCount(emotionId, 1);
        Long likeId = emotionLikeRepository.findByEmotionIdAndDeviceId(emotionId, device.getId()).orElseThrow().getId();
        assertThat(emotionLikeService.update(emotionId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(likeId);
        assertLikeCount(emotionId, 1);

        assertThat(emotionLikeService.update(emotionId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(emotionId, 0);
        assertThat(emotionLikeService.update(emotionId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(emotionId, 0);
        assertThat(emotionLikeService.update(emotionId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertLikeCount(emotionId, 1);
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(emotionId, device.getId()))
                .get().extracting(EmotionLike::getId).isNotEqualTo(likeId);
    }

    @Test
    void 좋아요를_취소해도_다른_기기나_다른_한숨의_좋아요는_유지한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Emotion anotherEmotion = emotionRepository.save(기본_한숨_빌더().build());
        saveLike(emotion.getId(), device.getId());
        EmotionLike anotherDeviceLike = saveLike(emotion.getId(), anotherDevice.getId());
        EmotionLike anotherEmotionLike = saveLike(anotherEmotion.getId(), device.getId());

        // when
        EmotionLikeResult result = emotionLikeService.update(emotion.getId(), device.getPublicId(), false);

        // then
        assertThat(result).isEqualTo(EmotionLikeResult.of(false, 1));
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(emotion.getId(), device.getId())).isEmpty();
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId)
                .containsExactlyInAnyOrder(anotherDeviceLike.getId(), anotherEmotionLike.getId());
        assertLikeCount(emotion.getId(), 1);
        assertLikeCount(anotherEmotion.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없는_기기나_없는_한숨이나_삭제된_한숨은_요청한_상태와_관계없이_거부한다(boolean liked) {
        // given
        EmotionLike like = saveLike(emotion.getId(), device.getId());

        // when / then
        assertThatThrownBy(() -> emotionLikeService.update(emotion.getId(), UUID.randomUUID(), liked))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> emotionLikeService.update(Long.MAX_VALUE, device.getPublicId(), liked))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
        Emotion latestEmotion = emotionRepository.findById(emotion.getId()).orElseThrow();
        latestEmotion.delete();
        emotionRepository.saveAndFlush(latestEmotion);
        assertThatThrownBy(() -> emotionLikeService.update(emotion.getId(), device.getPublicId(), liked))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(like.getId());
        assertLikeCount(emotion.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 트랜잭션이_실패하면_좋아요_생성이나_삭제와_개수가_함께_롤백된다(boolean desiredLiked) {
        // given
        EmotionLike original = desiredLiked ? null : saveLike(emotion.getId(), device.getId());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emotionLikeService.update(emotion.getId(), device.getPublicId(), desiredLiked);
            emotionLikeRepository.flush();
            assertLikeCount(emotion.getId(), desiredLiked ? 1 : 0);
            throw new IllegalStateException("저장 후 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("저장 후 실패");

        // then
        if (desiredLiked) {
            assertThat(emotionLikeRepository.findAll()).isEmpty();
        } else {
            assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(original.getId());
        }
        assertLikeCount(emotion.getId(), desiredLiked ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 낙관적_잠금_충돌이_발생하면_먼저_성공한_좋아요와_개수를_보존한다(boolean desiredLiked) {
        // given
        EmotionLike original = desiredLiked ? null : saveLike(emotion.getId(), device.getId());
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        TransactionTemplate anotherTransaction = new TransactionTemplate(transactionManager);
        anotherTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 바깥 트랜잭션이 읽은 버전을 유지한 채 다른 기기의 변경을 먼저 커밋한다.
            emotionRepository.findById(emotion.getId()).orElseThrow();
            anotherTransaction.executeWithoutResult(anotherStatus ->
                    emotionLikeService.update(emotion.getId(), anotherDevice.getPublicId(), true));
            emotionLikeService.update(emotion.getId(), device.getPublicId(), desiredLiked);
            emotionLikeRepository.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // then
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(emotion.getId(), anotherDevice.getId())).isPresent();
        if (desiredLiked) {
            assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(emotion.getId(), device.getId())).isEmpty();
        } else {
            assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(emotion.getId(), device.getId()))
                    .get().extracting(EmotionLike::getId).isEqualTo(original.getId());
        }
        assertLikeCount(emotion.getId(), desiredLiked ? 1 : 2);
    }

    private void assertLikeCount(Long emotionId, long expected) {
        long likeRowCount = jdbcClient.sql("SELECT COUNT(*) FROM emotion_likes WHERE emotion_id = :emotionId")
                .param("emotionId", emotionId)
                .query(Long.class)
                .single();

        assertThat(likeRowCount).isEqualTo(expected);
        assertThat(emotionRepository.findById(emotionId).orElseThrow().getLikeCount()).isEqualTo(expected);
    }

    private EmotionLike saveLike(Long emotionId, Long deviceId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            emotionRepository.findById(emotionId).orElseThrow().increaseLikeCount();
            return emotionLikeRepository.save(기본_좋아요_빌더()
                    .emotionId(emotionId)
                    .deviceId(deviceId)
                    .build());
        });
    }
}
