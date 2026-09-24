package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.서울시청_좌표;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.application.query.dto.EmotionListItemView;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void 감정은_스탬프_없이_저장하거나_같은_그룹_스탬프를_공유하여_조회한다() {
        // given
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        Emotion first = saveEmotionWithStamp(stamp);
        Emotion second = saveEmotionWithStamp(stamp);
        entityManager.flush();
        entityManager.clear();

        // when / then
        for (Emotion target : List.of(emotion, first, second)) {
            assertThat(emotionQueryService.findById(target.getId(), viewer.getPublicId()).id())
                    .isEqualTo(target.getId());
        }
        assertThat(entityManager.find(Emotion.class, emotion.getId()).getGroupStamp()).isNull();
        for (Emotion target : List.of(first, second)) {
            GroupStamp loadedStamp = entityManager.find(Emotion.class, target.getId()).getGroupStamp();
            assertThat(loadedStamp.getId()).isEqualTo(stamp.getId());
            assertThat(loadedStamp.getText()).isEqualTo("기본");
        }

        // when
        GroupStamp updatedStamp = entityManager.find(GroupStamp.class, stamp.getId());
        updatedStamp.change("변경", "#000000", "#FFFFFF", updatedStamp.getFrame());
        entityManager.flush();
        entityManager.clear();

        // then
        for (Emotion target : List.of(first, second)) {
            emotionQueryService.findById(target.getId(), viewer.getPublicId());
            GroupStamp loadedStamp = entityManager.find(Emotion.class, target.getId()).getGroupStamp();
            assertThat(loadedStamp.getText()).isEqualTo("변경");
            assertThat(loadedStamp.getTextColor()).isEqualTo("#000000");
            assertThat(loadedStamp.getBackgroundColor()).isEqualTo("#FFFFFF");
        }
    }

    @ParameterizedTest
    @MethodSource("emotionContents")
    void 감정_내용을_저장하고_상세와_목록에서_다시_조회한다(String memo, Audio audio) {
        // given
        Emotion saved = emotionRepository.save(기본_한숨_빌더()
                .deviceId(author.getId())
                .state(EmotionState.FRUSTRATED)
                .memo(memo)
                .audio(audio)
                .build());
        entityManager.flush();
        entityManager.clear();
        String expectedMemo = memo == null ? null : memo.strip();

        // when
        EmotionDetailView detail = emotionQueryService.findById(saved.getId(), viewer.getPublicId());
        Emotion loaded = entityManager.find(Emotion.class, saved.getId());

        // then
        assertThat(detail.memo()).isEqualTo(expectedMemo);
        assertThat(loaded.getContent()).isNotNull();
        assertThat(loaded.getMemo()).isEqualTo(expectedMemo);
        assertThat(loaded.getContent().getAudio()).isEqualTo(audio);

        // when
        entityManager.clear();
        Instant snapshotAt = Instant.now().plusSeconds(1);
        List<EmotionListItemView> items = emotionQueryService.findVisiblePageWithinBounds(
                EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0),
                snapshotAt, snapshotAt, Long.MAX_VALUE, 10, viewer.getPublicId());

        // then
        assertThat(items).filteredOn(item -> item.id().equals(saved.getId()))
                .singleElement().satisfies(item -> assertThat(item.memo()).isEqualTo(expectedMemo));
        assertThat(entityManager.find(Emotion.class, saved.getId()).getContent().getAudio()).isEqualTo(audio);
    }

    @Test
    void 감정_생성도_메모와_녹음의_동시_등록을_거부한다() {
        Audio audio = Audio.builder().objectKey("recordings/voice.m4a").build();

        assertThatThrownBy(() -> 기본_한숨_빌더().memo("메모").audio(audio).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메모와 녹음은 함께 등록할 수 없습니다.");
    }

    @Test
    void DB도_메모와_녹음의_동시_저장을_거부한다() {
        // given
        entityManager.flush();

        // when / then
        assertThatThrownBy(() -> jdbcClient.sql("UPDATE emotions SET audio_object_key = :key WHERE id = :id")
                .param("key", "recordings/voice.m4a")
                .param("id", emotion.getId())
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_emotions_content_exclusive");
    }

    @Test
    void 목록은_기간_제한없이_조회하고_삭제와_차단_및_영역_밖_감정을_제외한다() {
        // given
        Emotion blockedEmotion = saveEmotion(author.getId(), 126.9774, 37.5669);
        Device blockedAuthor = deviceRepository.save(기본_기기_빌더().build());
        Emotion blockedAuthorEmotion = saveEmotion(blockedAuthor.getId(), 126.9774, 37.5669);
        Emotion deletedEmotion = saveEmotion(author.getId(), 126.9774, 37.5669);
        saveEmotion(author.getId(), 129.0, 37.5669);
        deletedEmotion.delete();
        emotionBlockRepository.save(EmotionBlock.builder()
                .blockerDeviceId(viewer.getId())
                .emotionId(blockedEmotion.getId())
                .build());
        deviceBlockRepository.save(DeviceBlock.builder()
                .blockerDeviceId(viewer.getId())
                .blockedDeviceId(blockedAuthor.getId())
                .originEmotionId(blockedAuthorEmotion.getId())
                .build());
        entityManager.flush();
        setCreatedAt(emotion, Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.clear();
        Instant snapshotAt = Instant.now().plusSeconds(1);

        // when
        List<EmotionListItemView> result = emotionQueryService.findVisiblePageWithinBounds(
                EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0),
                snapshotAt, snapshotAt, Long.MAX_VALUE, 10, viewer.getPublicId()
        );

        // then
        assertThat(result).extracting(EmotionListItemView::id).containsExactly(emotion.getId());
        assertThat(result.getFirst().state()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(result.getFirst().rotationDegrees()).isEqualTo(35.5);
        assertThat(result.getFirst().longitude()).isEqualTo(126.9774);
        assertThat(result.getFirst().latitude()).isEqualTo(37.5669);
    }

    @Test
    void 목록은_스냅샷과_생성시각_ID_커서로_중복없이_이어진다() {
        // given
        Emotion sameTime = saveEmotion(author.getId(), 126.9774, 37.5669);
        Emotion older = saveEmotion(author.getId(), 126.9774, 37.5669);
        Emotion afterSnapshot = saveEmotion(author.getId(), 126.9774, 37.5669);
        entityManager.flush();
        Instant newestAt = Instant.parse("2025-01-03T00:00:00Z");
        setCreatedAt(emotion, newestAt);
        setCreatedAt(sameTime, newestAt);
        setCreatedAt(older, Instant.parse("2025-01-02T00:00:00Z"));
        setCreatedAt(afterSnapshot, Instant.parse("2025-01-05T00:00:00Z"));
        entityManager.clear();
        Instant snapshotAt = Instant.parse("2025-01-04T00:00:00Z");
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0);

        // when
        List<EmotionListItemView> firstPage = emotionQueryService.findVisiblePageWithinBounds(
                bounds, snapshotAt, snapshotAt, Long.MAX_VALUE, 2, viewer.getPublicId()
        );
        EmotionListItemView lastItem = firstPage.getLast();
        List<EmotionListItemView> nextPage = emotionQueryService.findVisiblePageWithinBounds(
                bounds, snapshotAt, lastItem.createdAt(), lastItem.id(), 2, viewer.getPublicId()
        );

        // then
        assertThat(firstPage).extracting(EmotionListItemView::id).containsExactly(sameTime.getId(), emotion.getId());
        assertThat(nextPage).extracting(EmotionListItemView::id).containsExactly(older.getId());
    }

    @Test
    void 날짜_변경선을_넘는_영역에서_양쪽_감정을_한_번씩_조회한다() {
        // given
        Emotion east = saveEmotion(author.getId(), 179.0, 0.0);
        Emotion west = saveEmotion(author.getId(), -179.0, 0.0);
        saveEmotion(author.getId(), -160.0, 0.0);
        Instant snapshotAt = Instant.now().plusSeconds(1);

        // when
        List<EmotionListItemView> result = emotionQueryService.findVisiblePageWithinBounds(
                EmotionSearchBounds.of(170.0, -10.0, -170.0, 10.0),
                snapshotAt, snapshotAt, Long.MAX_VALUE, 10, viewer.getPublicId()
        );

        // then
        assertThat(result).extracting(EmotionListItemView::id).containsExactly(west.getId(), east.getId());
    }

    private static Stream<Arguments> emotionContents() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("  " + "😀".repeat(200) + "  ", null),
                Arguments.of(null, Audio.builder().objectKey("recordings/기기/음성 기록.m4a").build())
        );
    }

    private Emotion saveEmotionWithStamp(GroupStamp stamp) {
        return emotionRepository.save(Emotion.builder()
                .requestId(UUID.randomUUID())
                .location(서울시청_좌표())
                .state(EmotionState.FRUSTRATED)
                .nickname("먼지구름")
                .deviceId(author.getId())
                .groupStamp(stamp)
                .build());
    }

    private Emotion saveEmotion(Long authorId, double longitude, double latitude) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        return emotionRepository.save(Emotion.builder()
                .requestId(UUID.randomUUID())
                .location(location)
                .state(EmotionState.FRUSTRATED)
                .nickname("먼지구름")
                .deviceId(authorId)
                .build());
    }

    private void setCreatedAt(Emotion target, Instant createdAt) {
        jdbcClient.sql("UPDATE emotions SET created_at = CAST(:createdAt AS TIMESTAMPTZ) WHERE id = :id")
                .param("createdAt", createdAt.toString())
                .param("id", target.getId())
                .update();
    }

    private void assertNotVisible() {
        assertThatThrownBy(() -> emotionQueryService.findById(emotion.getId(), viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
    }
}
