package com.pheeeew.emotion.application.emoji;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionEmoji;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import(EmotionEmojiService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionEmojiServiceIntegrationTest {

    @Autowired
    private EmotionEmojiService emotionEmojiService;

    @Autowired
    private EmotionEmojiRepository emotionEmojiRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Device device;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.save(기본_한숨_빌더().build());
    }

    @AfterEach
    void tearDown() {
        emotionEmojiRepository.deleteAllInBatch();
        emotionRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 같은_선택과_취소를_재시도해도_해당_이모지의_상태만_유지한다() {
        // given
        Long emotionId = emotion.getId();
        UUID devicePublicId = device.getPublicId();

        // when / then
        emotionEmojiService.update(emotionId, devicePublicId, EmojiType.HEART, true);
        Long heartId = emotionEmojiRepository.findAll().getFirst().getId();
        emotionEmojiService.update(emotionId, devicePublicId, EmojiType.HEART, true);
        assertThat(emotionEmojiRepository.findAll()).extracting(EmotionEmoji::getId).containsExactly(heartId);

        emotionEmojiService.update(emotionId, devicePublicId, EmojiType.LAUGH, true);
        emotionEmojiService.update(emotionId, devicePublicId, EmojiType.HEART, false);
        emotionEmojiService.update(emotionId, devicePublicId, EmojiType.HEART, false);
        assertThat(emotionEmojiRepository.findAll()).extracting(EmotionEmoji::getEmojiType)
                .containsExactly(EmojiType.LAUGH);
    }

    @Test
    void 선택이_없어도_여섯_이모지를_0과_미선택으로_조회한다() {
        // when
        List<EmotionEmojiResult> results = emotionEmojiService.findAll(emotion.getId(), device.getPublicId());

        // then
        assertThat(results).containsExactly(
                EmotionEmojiResult.of(EmojiType.HEART, 0, false),
                EmotionEmojiResult.of(EmojiType.LAUGH, 0, false),
                EmotionEmojiResult.of(EmojiType.CRY, 0, false),
                EmotionEmojiResult.of(EmojiType.DIZZY, 0, false),
                EmotionEmojiResult.of(EmojiType.RAGE, 0, false),
                EmotionEmojiResult.of(EmojiType.SKULL, 0, false)
        );
    }

    @Test
    void 종류별_전체_선택_수와_인증된_기기의_선택_여부를_조회한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, true);
        emotionEmojiService.update(emotion.getId(), anotherDevice.getPublicId(), EmojiType.HEART, true);
        emotionEmojiService.update(emotion.getId(), anotherDevice.getPublicId(), EmojiType.LAUGH, true);
        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.SKULL, true);

        // when
        List<EmotionEmojiResult> mine = emotionEmojiService.findAll(emotion.getId(), device.getPublicId());
        List<EmotionEmojiResult> theirs = emotionEmojiService.findAll(emotion.getId(), anotherDevice.getPublicId());

        // then
        assertThat(mine).containsExactly(
                EmotionEmojiResult.of(EmojiType.HEART, 2, true),
                EmotionEmojiResult.of(EmojiType.LAUGH, 1, false),
                EmotionEmojiResult.of(EmojiType.CRY, 0, false),
                EmotionEmojiResult.of(EmojiType.DIZZY, 0, false),
                EmotionEmojiResult.of(EmojiType.RAGE, 0, false),
                EmotionEmojiResult.of(EmojiType.SKULL, 1, true)
        );
        assertThat(theirs.getFirst()).isEqualTo(EmotionEmojiResult.of(EmojiType.HEART, 2, true));
        assertThat(theirs.get(1)).isEqualTo(EmotionEmojiResult.of(EmojiType.LAUGH, 1, true));
        assertThat(theirs.getLast()).isEqualTo(EmotionEmojiResult.of(EmojiType.SKULL, 1, false));

        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, false);
        assertThat(emotionEmojiService.findAll(emotion.getId(), device.getPublicId()).getFirst())
                .isEqualTo(EmotionEmojiResult.of(EmojiType.HEART, 1, false));
    }

    @Test
    void 없는_기기나_감정과_삭제된_감정의_이모지_조회는_거부한다() {
        // when / then
        assertThatThrownBy(() -> emotionEmojiService.findAll(emotion.getId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> emotionEmojiService.findAll(Long.MAX_VALUE, device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));

        Emotion deleted = emotionRepository.findById(emotion.getId()).orElseThrow();
        deleted.delete();
        emotionRepository.saveAndFlush(deleted);
        assertThatThrownBy(() -> emotionEmojiService.findAll(emotion.getId(), device.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
    }

    @Test
    void 이모지_취소는_다른_기기와_다른_감정의_선택을_보존한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Emotion anotherEmotion = emotionRepository.save(기본_한숨_빌더().build());
        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, true);
        emotionEmojiService.update(emotion.getId(), anotherDevice.getPublicId(), EmojiType.HEART, true);
        emotionEmojiService.update(anotherEmotion.getId(), device.getPublicId(), EmojiType.HEART, true);

        // when
        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, false);

        // then
        assertThat(emotionEmojiRepository.findAll())
                .extracting(EmotionEmoji::getEmotionId, EmotionEmoji::getDeviceId, EmotionEmoji::getEmojiType)
                .containsExactlyInAnyOrder(
                        tuple(emotion.getId(), anotherDevice.getId(), EmojiType.HEART),
                        tuple(anotherEmotion.getId(), device.getId(), EmojiType.HEART)
                );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없는_기기나_감정과_삭제된_감정의_이모지_요청은_거부한다(boolean selected) {
        // when / then
        assertThatThrownBy(() -> emotionEmojiService.update(emotion.getId(), UUID.randomUUID(), EmojiType.HEART, selected))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
        assertThatThrownBy(() -> emotionEmojiService.update(Long.MAX_VALUE, device.getPublicId(), EmojiType.HEART, selected))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));

        Emotion deleted = emotionRepository.findById(emotion.getId()).orElseThrow();
        deleted.delete();
        emotionRepository.saveAndFlush(deleted);
        assertThatThrownBy(() -> emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, selected))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND));
        assertThat(emotionEmojiRepository.findAll()).isEmpty();
    }

    @Test
    void 트랜잭션이_실패하면_이모지_선택과_취소를_롤백한다() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when / then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, true);
            throw new IllegalStateException("선택 후 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(emotionEmojiRepository.findAll()).isEmpty();

        emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, true);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, false);
            throw new IllegalStateException("취소 후 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(emotionEmojiRepository.findAll()).extracting(EmotionEmoji::getEmojiType)
                .containsExactly(EmojiType.HEART);
    }

    @Test
    void 같은_이모지를_동시에_선택해도_한_건만_저장한다() throws Exception {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> requests = List.of(
                    executor.submit(() -> selectWhenStarted(ready, start)),
                    executor.submit(() -> selectWhenStarted(ready, start))
            );

            // when
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> request : requests) {
                request.get(10, TimeUnit.SECONDS);
            }

            // then
            assertThat(emotionEmojiRepository.findAll()).extracting(EmotionEmoji::getEmojiType)
                    .containsExactly(EmojiType.HEART);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void selectWhenStarted(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            emotionEmojiService.update(emotion.getId(), device.getPublicId(), EmojiType.HEART, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
