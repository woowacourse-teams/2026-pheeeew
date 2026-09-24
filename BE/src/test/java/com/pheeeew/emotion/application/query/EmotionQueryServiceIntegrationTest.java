package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({EmotionQueryService.class, EmotionCommandService.class})
class EmotionQueryServiceIntegrationTest {

    @Autowired
    private EmotionQueryService emotionQueryService;

    @Autowired
    private EmotionCommandService emotionCommandService;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EmotionBlockRepository emotionBlockRepository;

    @Autowired
    private DeviceBlockRepository deviceBlockRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EntityManager entityManager;

    private Device viewer;
    private Device author;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        viewer = deviceRepository.save(기본_기기_빌더().build());
        author = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.save(Emotion.builder()
                .requestId(UUID.randomUUID())
                .location(서울시청_좌표())
                .state(EmotionState.FRUSTRATED)
                .rotationDegrees(35.5)
                .memo("답답한 하루")
                .nickname("먼지구름")
                .deviceId(author.getId())
                .build());
    }

    @Test
    void 선택이_없어도_여섯_이모지를_0과_미선택으로_조회한다() {
        List<EmotionEmojiResult> emojis = emotionQueryService.findById(emotion.getId(), viewer.getPublicId()).emojis();

        assertThat(emojis).containsExactly(
                EmotionEmojiResult.of(EmojiType.HEART, 0, false),
                EmotionEmojiResult.of(EmojiType.LAUGH, 0, false),
                EmotionEmojiResult.of(EmojiType.CRY, 0, false),
                EmotionEmojiResult.of(EmojiType.DIZZY, 0, false),
                EmotionEmojiResult.of(EmojiType.RAGE, 0, false),
                EmotionEmojiResult.of(EmojiType.SKULL, 0, false)
        );
    }

    @Test
    void 기간이_지난_감정도_이모지_집계와_인증된_기기의_선택_여부를_함께_조회한다() {
        // given
        emotionCommandService.updateEmoji(emotion.getId(), viewer.getPublicId(), EmojiType.HEART, true);
        emotionCommandService.updateEmoji(emotion.getId(), author.getPublicId(), EmojiType.HEART, true);
        emotionCommandService.updateEmoji(emotion.getId(), author.getPublicId(), EmojiType.LAUGH, true);
        Instant oldCreatedAt = Instant.parse("2025-01-01T00:00:00Z");
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = CAST(:createdAt AS TIMESTAMPTZ) WHERE id = :id")
                .param("createdAt", oldCreatedAt.toString())
                .param("id", emotion.getId())
                .update();
        entityManager.clear();

        // when
        EmotionDetailView result = emotionQueryService.findById(emotion.getId(), viewer.getPublicId());

        // then
        assertThat(result.id()).isEqualTo(emotion.getId());
        assertThat(result.createdAt()).isEqualTo(oldCreatedAt);
        assertThat(result.state()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(result.rotationDegrees()).isEqualTo(35.5);
        assertThat(result.memo()).isEqualTo("답답한 하루");
        assertThat(result.nickname()).isEqualTo("먼지구름");
        assertThat(result.emojis()).hasSize(6);
        assertThat(result.emojis().get(0).type()).isEqualTo(EmojiType.HEART);
        assertThat(result.emojis().get(0).count()).isEqualTo(2);
        assertThat(result.emojis().get(0).selected()).isTrue();
        assertThat(result.emojis().get(1).type()).isEqualTo(EmojiType.LAUGH);
        assertThat(result.emojis().get(1).count()).isEqualTo(1);
        assertThat(result.emojis().get(1).selected()).isFalse();

        EmotionDetailView authorView = emotionQueryService.findById(emotion.getId(), author.getPublicId());
        assertThat(authorView.emojis().get(0).selected()).isTrue();
        assertThat(authorView.emojis().get(1).selected()).isTrue();

        emotionCommandService.updateEmoji(emotion.getId(), viewer.getPublicId(), EmojiType.HEART, false);
        EmotionEmojiResult heartAfterCancel = emotionQueryService.findById(emotion.getId(), viewer.getPublicId())
                .emojis().getFirst();
        assertThat(heartAfterCancel).isEqualTo(EmotionEmojiResult.of(EmojiType.HEART, 1, false));
    }

    @Test
    void 삭제된_감정은_조회되지_않는다() {
        emotion.delete();
        entityManager.flush();

        assertNotVisible();
    }

    @Test
    void 차단한_감정은_조회되지_않는다() {
        emotionBlockRepository.save(EmotionBlock.builder()
                .blockerDeviceId(viewer.getId())
                .emotionId(emotion.getId())
                .build());

        assertNotVisible();
        assertThatThrownBy(() -> emotionCommandService.updateEmoji(
                emotion.getId(), viewer.getPublicId(), EmojiType.HEART, true))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
    }

    @Test
    void 차단한_작성자의_감정은_조회되지_않는다() {
        deviceBlockRepository.save(DeviceBlock.builder()
                .blockerDeviceId(viewer.getId())
                .blockedDeviceId(author.getId())
                .originEmotionId(emotion.getId())
                .build());

        assertNotVisible();
        assertThatThrownBy(() -> emotionCommandService.updateEmoji(
                emotion.getId(), viewer.getPublicId(), EmojiType.HEART, true))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
    }

    @Test
    void 없는_감정은_조회되지_않는다() {
        assertThatThrownBy(() -> emotionQueryService.findById(Long.MAX_VALUE, viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
    }

    @Test
    void 존재하지_않는_기기로는_조회할_수_없다() {
        assertThatThrownBy(() -> emotionQueryService.findById(emotion.getId(), UUID.randomUUID()))
                .isInstanceOf(DeviceException.class);
    }

    private void assertNotVisible() {
        assertThatThrownBy(() -> emotionQueryService.findById(emotion.getId(), viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
    }
}
