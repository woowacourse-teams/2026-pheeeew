package com.pheeeew.sigh.application.like;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기본_한숨_빌더;
import static com.pheeeew.sigh.fixture.SighLikeFixture.기본_좋아요_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.like.dto.EmotionLikeResult;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.EmotionLike;
import com.pheeeew.sigh.domain.repository.EmotionLikeRepository;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.exception.EmotionErrorCode;
import com.pheeeew.sigh.exception.EmotionException;
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
    private Emotion sigh;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(기본_기기_빌더().build());
        sigh = emotionRepository.save(기본_한숨_빌더().build());
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
        Long sighId = sigh.getId();
        UUID devicePublicId = device.getPublicId();

        // when / then
        assertThat(emotionLikeService.update(sighId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(emotionLikeService.update(sighId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertLikeCount(sighId, 1);
        Long likeId = emotionLikeRepository.findByEmotionIdAndDeviceId(sighId, device.getId()).orElseThrow().getId();
        assertThat(emotionLikeService.update(sighId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(likeId);
        assertLikeCount(sighId, 1);

        assertThat(emotionLikeService.update(sighId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(emotionLikeService.update(sighId, devicePublicId, false)).isEqualTo(EmotionLikeResult.of(false, 0));
        assertLikeCount(sighId, 0);
        assertThat(emotionLikeService.update(sighId, devicePublicId, true)).isEqualTo(EmotionLikeResult.of(true, 1));
        assertLikeCount(sighId, 1);
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(sighId, device.getId()))
                .get().extracting(EmotionLike::getId).isNotEqualTo(likeId);
    }

    @Test
    void 좋아요를_취소해도_다른_기기나_다른_한숨의_좋아요는_유지한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Emotion anotherSigh = emotionRepository.save(기본_한숨_빌더().build());
        saveLike(sigh.getId(), device.getId());
        EmotionLike anotherDeviceLike = saveLike(sigh.getId(), anotherDevice.getId());
        EmotionLike anotherSighLike = saveLike(anotherSigh.getId(), device.getId());

        // when
        EmotionLikeResult result = emotionLikeService.update(sigh.getId(), device.getPublicId(), false);

        // then
        assertThat(result).isEqualTo(EmotionLikeResult.of(false, 1));
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(sigh.getId(), device.getId())).isEmpty();
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId)
                .containsExactlyInAnyOrder(anotherDeviceLike.getId(), anotherSighLike.getId());
        assertLikeCount(sigh.getId(), 1);
        assertLikeCount(anotherSigh.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없는_기기나_없는_한숨이나_삭제된_한숨은_요청한_상태와_관계없이_거부한다(boolean liked) {
        // given
        EmotionLike like = saveLike(sigh.getId(), device.getId());

        // when / then
        assertThatThrownBy(() -> emotionLikeService.update(sigh.getId(), UUID.randomUUID(), liked))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> emotionLikeService.update(Long.MAX_VALUE, device.getPublicId(), liked))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
        Emotion latestSigh = emotionRepository.findById(sigh.getId()).orElseThrow();
        latestSigh.delete();
        emotionRepository.saveAndFlush(latestSigh);
        assertThatThrownBy(() -> emotionLikeService.update(sigh.getId(), device.getPublicId(), liked))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
        assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(like.getId());
        assertLikeCount(sigh.getId(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 트랜잭션이_실패하면_좋아요_생성이나_삭제와_개수가_함께_롤백된다(boolean desiredLiked) {
        // given
        EmotionLike original = desiredLiked ? null : saveLike(sigh.getId(), device.getId());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emotionLikeService.update(sigh.getId(), device.getPublicId(), desiredLiked);
            emotionLikeRepository.flush();
            assertLikeCount(sigh.getId(), desiredLiked ? 1 : 0);
            throw new IllegalStateException("저장 후 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("저장 후 실패");

        // then
        if (desiredLiked) {
            assertThat(emotionLikeRepository.findAll()).isEmpty();
        } else {
            assertThat(emotionLikeRepository.findAll()).extracting(EmotionLike::getId).containsExactly(original.getId());
        }
        assertLikeCount(sigh.getId(), desiredLiked ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 낙관적_잠금_충돌이_발생하면_먼저_성공한_좋아요와_개수를_보존한다(boolean desiredLiked) {
        // given
        EmotionLike original = desiredLiked ? null : saveLike(sigh.getId(), device.getId());
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        TransactionTemplate anotherTransaction = new TransactionTemplate(transactionManager);
        anotherTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());

        // when
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 바깥 트랜잭션이 읽은 버전을 유지한 채 다른 기기의 변경을 먼저 커밋한다.
            emotionRepository.findById(sigh.getId()).orElseThrow();
            anotherTransaction.executeWithoutResult(anotherStatus ->
                    emotionLikeService.update(sigh.getId(), anotherDevice.getPublicId(), true));
            emotionLikeService.update(sigh.getId(), device.getPublicId(), desiredLiked);
            emotionLikeRepository.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // then
        assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(sigh.getId(), anotherDevice.getId())).isPresent();
        if (desiredLiked) {
            assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(sigh.getId(), device.getId())).isEmpty();
        } else {
            assertThat(emotionLikeRepository.findByEmotionIdAndDeviceId(sigh.getId(), device.getId()))
                    .get().extracting(EmotionLike::getId).isEqualTo(original.getId());
        }
        assertLikeCount(sigh.getId(), desiredLiked ? 1 : 2);
    }

    private void assertLikeCount(Long sighId, long expected) {
        long likeRowCount = jdbcClient.sql("SELECT COUNT(*) FROM sigh_likes WHERE sigh_id = :sighId")
                .param("sighId", sighId)
                .query(Long.class)
                .single();

        assertThat(likeRowCount).isEqualTo(expected);
        assertThat(emotionRepository.findById(sighId).orElseThrow().getLikeCount()).isEqualTo(expected);
    }

    private EmotionLike saveLike(Long sighId, Long deviceId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            emotionRepository.findById(sighId).orElseThrow().increaseLikeCount();
            return emotionLikeRepository.save(기본_좋아요_빌더()
                    .emotionId(sighId)
                    .deviceId(deviceId)
                    .build());
        });
    }
}
