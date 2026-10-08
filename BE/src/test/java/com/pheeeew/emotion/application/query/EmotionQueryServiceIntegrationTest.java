package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.hibernate.SessionFactory;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({EmotionQueryService.class, EmotionCommandService.class, EmotionContentResolver.class})
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
    private DeviceService deviceService;

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
        검증용_지역_계층을_저장한다(jdbcClient);
        viewer = deviceRepository.save(기본_기기_빌더().build());
        author = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .state(EmotionState.FRUSTRATED)
                .rotationDegrees(35.5)
                .memo("답답한 하루")
                .deviceId(author.getId())
                .build());
        과거_닉네임을_저장한다(emotion);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 목록과_상세는_기명에만_현재_작성자_닉네임을_표시한다(boolean withoutBounds) {
        // given: 같은 작성자의 익명 감정이 일괄 조회한 실제 이름을 노출하면 안 된다.
        deviceService.updateNickname(author.getPublicId(), "작성자");
        Device other = deviceRepository.save(기본_기기_빌더().nickname("다른 이름").build());
        Emotion named = emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).anonymous(false).memo("기명").build());
        Emotion otherNamed = emotionRepository.save(기본_한숨_빌더().deviceId(other.getId()).anonymous(false).memo("다른 기명").build());
        Emotion withoutAuthor = saveEmotion(null, 126.9774, 37.5669);
        entityManager.flush();
        과거_닉네임을_저장한다(named);
        entityManager.clear();
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126, 37, 128, 38);

        // when
        List<EmotionDetailView> items = (withoutBounds
                ? emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null)
                : emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null)).items();

        // then
        assertThat(items).extracting(EmotionDetailView::id, EmotionDetailView::nickname).containsExactlyInAnyOrder(
                tuple(emotion.getId(), "익명"), tuple(withoutAuthor.getId(), "익명"),
                tuple(named.getId(), "작성자"), tuple(otherNamed.getId(), "다른 이름"));
        assertThat(emotionQueryService.findById(named.getId(), viewer.getPublicId()).nickname()).isEqualTo("작성자");
        assertThat(emotionQueryService.findById(otherNamed.getId(), viewer.getPublicId()).nickname()).isEqualTo("다른 이름");
        assertThat(emotionQueryService.findById(emotion.getId(), viewer.getPublicId()).nickname()).isEqualTo("익명");
        assertThat(emotionQueryService.findById(withoutAuthor.getId(), viewer.getPublicId()).nickname()).isEqualTo("익명");
    }

    @Test
    void 닉네임_변경은_기명_상세와_첫_페이지와_기존_커서의_다음_페이지에도_반영한다() {
        // given
        deviceService.updateNickname(author.getPublicId(), "이전 이름");
        for (int index = 0; index < 21; index++) {
            emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).anonymous(false).memo("기명").build());
        }
        entityManager.flush();
        entityManager.clear();
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        EmotionPageView before = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        assertThat(before.items()).hasSize(20).extracting(EmotionDetailView::nickname).containsOnly("이전 이름");

        // when
        deviceService.updateNickname(author.getPublicId(), "새 이름");
        entityManager.clear();
        EmotionPageView first = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        EmotionPageView next = emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, before.nextCursor());

        // then
        assertThat(first.items()).hasSize(20).extracting(EmotionDetailView::nickname).containsOnly("새 이름");
        assertThat(next.items()).hasSize(2).extracting(EmotionDetailView::nickname).containsExactlyInAnyOrder("새 이름", "익명");
        assertThat(emotionQueryService.findById(first.items().getFirst().id(), viewer.getPublicId()).nickname()).isEqualTo("새 이름");
        assertThat(jdbcClient.sql("SELECT nickname FROM emotions WHERE id = :id")
                .param("id", emotion.getId()).query(String.class).single()).isEqualTo("먼지구름");
    }

    @Test
    void 기명_감정의_작성자_닉네임이_없어도_과거_랜덤_이름을_표시하지_않는다() {
        // given: 전환 중 불완전한 기명 기록도 안전하게 익명으로 표시한다.
        Emotion named = emotionRepository.saveAndFlush(기본_한숨_빌더().deviceId(author.getId()).anonymous(false).memo("기명").build());
        과거_닉네임을_저장한다(named);
        entityManager.clear();

        // when / then
        assertThat(emotionQueryService.findById(named.getId(), viewer.getPublicId()).nickname()).isEqualTo("익명");
        assertThat(emotionQueryService.findListWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId(), null, null).items())
                .extracting(EmotionDetailView::nickname).containsOnly("익명");
    }

    @Test
    void 기명_목록의_닉네임_조회는_감정_수와_관계없이_SQL_한_번만_추가한다() {
        // given
        deviceService.updateNickname(author.getPublicId(), "작성자");
        deviceService.updateNickname(viewer.getPublicId(), "조회자");
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            EmotionSearchBounds bounds = EmotionSearchBounds.of(126, 37, 128, 38);
            statistics.clear();
            emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
            long anonymousStatements = statistics.getPrepareStatementCount();
            for (int index = 0; index < 10; index++) {
                emotionRepository.save(기본_한숨_빌더().deviceId(index % 2 == 0 ? author.getId() : viewer.getId())
                        .anonymous(false).memo("기명").build());
            }
            entityManager.flush();
            entityManager.clear();
            statistics.clear();

            // when
            EmotionPageView page = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);

            // then: 작성자 두 기기의 이름을 감정 열 건마다 조회하지 않는다.
            assertThat(page.items()).hasSize(11).extracting(EmotionDetailView::nickname)
                    .containsOnly("작성자", "조회자", "익명");
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(anonymousStatements + 1);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @ParameterizedTest
    @CsvSource({"1, false", "10, false", "10, true"})
    void 목록의_작성자와_스탬프가_늘어도_이모지까지_일괄_조회한다(int count, boolean withoutBounds) {
        // given
        List<Tuple> expected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Device writer = deviceRepository.save(기본_기기_빌더().nickname("작성자" + (char) ('A' + index)).build());
            Group group = 기본_그룹_빌더().name("그룹" + index).build();
            entityManager.persist(group);
            GroupStamp stamp = 기본_스탬프_빌더(group).build();
            entityManager.persist(stamp);

            Emotion target = emotionRepository.save(기본_한숨_빌더().deviceId(writer.getId())
                    .anonymous(false).memo("메모").groupStamp(stamp).build());
            emotionCommandService.updateEmoji(target.getId(), viewer.getPublicId(), EmojiType.HEART, true);
            expected.add(tuple(target.getId(), writer.getNickname(), GroupStampResult.from(stamp)));
        }

        entityManager.flush();
        entityManager.clear();

        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        try {
            // when
            EmotionPageView page = withoutBounds
                    ? emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null)
                    : emotionQueryService.findListWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId(), null, null);

            // then: 조회 기기, 감정, 이모지, 그룹 스탬프, 작성자마다 한 번씩 조회한다.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
            assertThat(page.items()).hasSize(count + 1);

            assertThat(page.items()).filteredOn(item -> !item.id().equals(emotion.getId()))
                    .extracting(EmotionDetailView::id, EmotionDetailView::nickname, EmotionDetailView::groupStamp)
                    .containsExactlyInAnyOrderElementsOf(expected);
            assertThat(page.items()).filteredOn(item -> !item.id().equals(emotion.getId()))
                    .allSatisfy(item -> {
                        assertThat(item.emojis()).hasSize(6).contains(EmotionEmojiResult.of(EmojiType.HEART, 1, true));
                        assertThat(item.isMine()).isFalse();
                    });
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    void 상세는_인증된_작성_기기만_본인으로_판별하고_작성_기기가_없으면_타인으로_본다() {
        // given
        Emotion withoutAuthor = emotionRepository.save(기본_한숨_빌더()
                .state(EmotionState.FRUSTRATED)
                .build());

        // when
        EmotionDetailView authorView = emotionQueryService.findById(emotion.getId(), author.getPublicId());
        EmotionDetailView otherView = emotionQueryService.findById(emotion.getId(), viewer.getPublicId());
        EmotionDetailView withoutAuthorView = emotionQueryService.findById(withoutAuthor.getId(), viewer.getPublicId());

        // then
        assertThat(authorView.isMine()).isTrue();
        assertThat(otherView.isMine()).isFalse();
        assertThat(withoutAuthorView.isMine()).isFalse();
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
        assertThat(result.nickname()).isEqualTo("익명");
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
        assertThat(emotionQueryService.findById(emotion.getId(), viewer.getPublicId()).groupStamp()).isNull();
        assertThat(emotionQueryService.findById(first.getId(), viewer.getPublicId()).groupStamp().text())
                .isEqualTo("기본");
        assertThat(emotionQueryService.findById(first.getId(), viewer.getPublicId()).groupId()).isEqualTo(group.getPublicId());
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
        assertThat(emotionQueryService.findById(first.getId(), viewer.getPublicId()).groupStamp().text())
                .isEqualTo("변경");
        assertThat(emotionQueryService.findListWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId(), null, null).items())
                .filteredOn(item -> item.id().equals(first.getId()) || item.id().equals(second.getId()))
                .hasSize(2).allSatisfy(item -> {
                    assertThat(item.groupStamp().text()).isEqualTo("변경");
                    assertThat(item.groupId()).isEqualTo(group.getPublicId());
                });
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
    void 감정_내용은_저장되고_녹음_상세는_발급기가_필요하다(String memo, Audio audio) {
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
        if (audio == null) {
            assertThat(emotionQueryService.findById(saved.getId(), viewer.getPublicId()).memo())
                    .isEqualTo(expectedMemo);
        } else {
            assertThatThrownBy(() -> emotionQueryService.findById(saved.getId(), viewer.getPublicId()))
                    .isInstanceOfSatisfying(EmotionException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE));
        }
        Emotion loaded = entityManager.find(Emotion.class, saved.getId());

        // then
        assertThat(loaded.getContent()).isNotNull();
        assertThat(loaded.getRegionCode()).isEqualTo(saved.getRegionCode()).isNotNull();
        assertThat(loaded.getRegionClassifiedAt()).isNotNull();
        assertThat(loaded.getMemo()).isEqualTo(expectedMemo);
        assertThat(loaded.getContent().getAudio()).isEqualTo(audio);

        // when
        entityManager.clear();
        List<EmotionMapItemView> items = emotionQueryService.findMapWithinBounds(
                EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0), viewer.getPublicId(), null, null).items();

        // then
        assertThat(items).extracting(EmotionMapItemView::id).contains(saved.getId());
        Emotion mapped = entityManager.find(Emotion.class, saved.getId());
        assertThat(mapped.getMemo()).isEqualTo(expectedMemo);
        assertThat(mapped.getContent().getAudio()).isEqualTo(audio);
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

        // when
        List<EmotionDetailView> result = emotionQueryService.findListWithinBounds(
                EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0), viewer.getPublicId(), null, null).items();

        // then
        assertThat(result).extracting(EmotionDetailView::id).containsExactly(emotion.getId());
        assertThat(result.getFirst().state()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(result.getFirst().rotationDegrees()).isEqualTo(35.5);
        assertThat(result.getFirst().longitude()).isEqualTo(126.9774);
        assertThat(result.getFirst().latitude()).isEqualTo(37.5669);
        assertThat(emotionQueryService.findMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId(), null, null).items()).extracting(EmotionMapItemView::id).containsExactly(emotion.getId());
    }

    @Test
    void 목록은_스냅샷과_생성시각_ID_커서로_중복없이_이어진다() {
        // given
        List<Emotion> sameTime = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            sameTime.add(saveEmotion(author.getId(), 126.9774, 37.5669));
        }
        Emotion older = saveEmotion(author.getId(), 126.9774, 37.5669);
        Emotion afterSnapshot = saveEmotion(author.getId(), 126.9774, 37.5669);
        entityManager.flush();
        Instant newestAt = Instant.parse("2025-01-03T00:00:00Z");
        setCreatedAt(emotion, newestAt);
        sameTime.forEach(item -> setCreatedAt(item, newestAt));
        setCreatedAt(older, Instant.parse("2025-01-02T00:00:00Z"));
        setCreatedAt(afterSnapshot, Instant.now().plusSeconds(60));
        entityManager.clear();
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0);

        // when
        EmotionPageView firstPage = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        EmotionPageView nextPage = emotionQueryService.findListWithinBounds(
                null, viewer.getPublicId(), null, firstPage.nextCursor());

        // then
        assertThat(firstPage.items()).extracting(EmotionDetailView::id)
                .containsExactlyElementsOf(sameTime.reversed().stream().map(Emotion::getId).toList());
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();
        assertThat(nextPage.items()).extracting(EmotionDetailView::id).containsExactly(emotion.getId(), older.getId());
        assertThat(nextPage.hasNext()).isFalse();
        assertThat(nextPage.nextCursor()).isNull();
    }

    @Test
    void 날짜_변경선을_넘는_영역에서_양쪽_감정을_한_번씩_조회한다() {
        // given
        Emotion east = saveEmotion(author.getId(), 179.0, 0.0);
        Emotion west = saveEmotion(author.getId(), -179.0, 0.0);
        saveEmotion(author.getId(), -160.0, 0.0);

        // when
        List<EmotionDetailView> result = emotionQueryService.findListWithinBounds(
                EmotionSearchBounds.of(170.0, -10.0, -170.0, 10.0), viewer.getPublicId(), null, null).items();

        // then
        assertThat(result).extracting(EmotionDetailView::id).containsExactly(west.getId(), east.getId());
        assertThat(emotionQueryService.findMapWithinBounds(EmotionSearchBounds.of(170, -10, -170, 10), viewer.getPublicId(), null, null).items()).extracting(EmotionMapItemView::id).containsExactly(west.getId(), east.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 목록은_혼합된_감정마다_인증된_작성_기기_여부를_계산한다(boolean withoutBounds) {
        // given
        Emotion viewerEmotion = saveEmotion(viewer.getId(), 126.9774, 37.5669);
        Emotion withoutAuthor = saveEmotion(null, 126.9774, 37.5669);
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126, 37, 128, 38);

        // when
        List<EmotionDetailView> authorItems = (withoutBounds
                ? emotionQueryService.findListWithoutBounds(author.getPublicId(), null, null)
                : emotionQueryService.findListWithinBounds(bounds, author.getPublicId(), null, null)).items();
        List<EmotionDetailView> viewerItems = (withoutBounds
                ? emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null)
                : emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null)).items();

        // then
        assertThat(authorItems).extracting(EmotionDetailView::id, EmotionDetailView::isMine)
                .containsExactlyInAnyOrder(
                        tuple(emotion.getId(), true),
                        tuple(viewerEmotion.getId(), false),
                        tuple(withoutAuthor.getId(), false));
        assertThat(viewerItems).extracting(EmotionDetailView::id, EmotionDetailView::isMine)
                .containsExactlyInAnyOrder(
                        tuple(emotion.getId(), false),
                        tuple(viewerEmotion.getId(), true),
                        tuple(withoutAuthor.getId(), false));
    }

    @Test
    void 내용_있는_지도는_NONE을_페이지_제한_전에_제외하고_다음_페이지에서도_그룹과_가시성을_유지한다() {
        // given
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            Emotion saved = 기본_한숨_빌더().memo("지도 메모").deviceId(author.getId()).groupStamp(stamp).build();
            entityManager.persist(saved);
            expected.add(saved.getId());
        }
        Emotion audio = 기본_한숨_빌더().audio(Audio.builder().objectKey("recordings/map.m4a").build())
                .deviceId(author.getId()).groupStamp(stamp).build();
        entityManager.persist(audio);
        expected.add(audio.getId());
        // 내용 없는 기록이 최신 200개보다 많아도 첫 페이지를 비우지 않아야 한다.
        for (int i = 0; i < 205; i++) {
            entityManager.persist(기본_한숨_빌더().deviceId(author.getId()).groupStamp(stamp).build());
        }
        Emotion deleted = 기본_한숨_빌더().memo("삭제").groupStamp(stamp).build();
        deleted.delete();
        entityManager.persist(deleted);
        Emotion blocked = 기본_한숨_빌더().memo("차단").groupStamp(stamp).build();
        entityManager.persist(blocked);
        emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        Device blockedAuthor = deviceRepository.save(기본_기기_빌더().build());
        Emotion blockedAuthorEmotion = 기본_한숨_빌더().memo("작성자 차단")
                .deviceId(blockedAuthor.getId()).groupStamp(stamp).build();
        entityManager.persist(blockedAuthorEmotion);
        deviceBlockRepository.save(DeviceBlock.builder().blockerDeviceId(viewer.getId())
                .blockedDeviceId(blockedAuthor.getId()).originEmotionId(blockedAuthorEmotion.getId()).build());
        entityManager.persist(기본_한숨_빌더().memo("영역 밖").groupStamp(stamp).location(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(129, 35))).build());
        Emotion future = 기본_한숨_빌더().memo("미래").groupStamp(stamp).build();
        entityManager.persist(future);
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        jdbcClient.sql("UPDATE emotions SET created_at = '2100-01-01T00:00:00Z' WHERE id = :id")
                .param("id", future.getId()).update();
        entityManager.clear();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);

        // when
        var first = emotionQueryService.findContentMapWithinBounds(bounds, viewer.getPublicId(), group.getPublicId(), null);
        var second = emotionQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), null, first.nextCursor());

        // then
        assertThat(first.items()).hasSize(200);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).hasSize(2);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        var all = Stream.concat(first.items().stream(), second.items().stream()).toList();
        assertThat(all).extracting(EmotionMapItemView::id).containsExactlyElementsOf(expected.reversed());
        assertThat(all).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(group.getPublicId()));
        var legacy = emotionQueryService.findMapWithinBounds(bounds, viewer.getPublicId(), group.getPublicId(), null);
        assertThat(legacy.items()).hasSize(200).extracting(EmotionMapItemView::id)
                .doesNotContainAnyElementsOf(expected);
        assertThat(emotionQueryService.findContentMapWithinBounds(bounds, viewer.getPublicId(), UUID.randomUUID(), null).items())
                .isEmpty();
    }

    @Test
    void 그룹_필터는_페이지_제한_전에_적용하고_다음_페이지에서도_유지한다() {
        // given: 조회 기기는 그룹 멤버가 아니어도 공개 감정을 조회한다.
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        Emotion deleted = saveEmotionWithStamp(stamp);
        deleted.delete();
        Emotion blocked = saveEmotionWithStamp(stamp);
        emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        for (int i = 0; i < 21; i++) {
            saveEmotionWithStamp(stamp);
        }
        Group otherGroup = 기본_그룹_빌더().name("다른 그룹").build();
        entityManager.persist(otherGroup);
        GroupStamp otherStamp = 기본_스탬프_빌더(otherGroup).build();
        entityManager.persist(otherStamp);
        Emotion other = saveEmotionWithStamp(otherStamp);
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);

        // when
        var first = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), group.getPublicId(), null);
        var second = emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, first.nextCursor());

        // then
        assertThat(first.items()).hasSize(20).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(group.getPublicId()));
        assertThat(second.items()).hasSize(1).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(group.getPublicId()));
        assertThat(second.hasNext()).isFalse();
        assertThat(first.items()).extracting(EmotionDetailView::id).doesNotContain(deleted.getId(), blocked.getId(), other.getId());
        assertThat(emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), otherGroup.getPublicId(), null).items())
                .extracting(EmotionDetailView::id).containsExactly(other.getId());
        var all = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        var allNext = emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, all.nextCursor());
        assertThat(Stream.concat(all.items().stream(), allNext.items().stream()).map(EmotionDetailView::id).toList())
                .contains(other.getId(), emotion.getId()).hasSize(23);
        assertThat(emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), UUID.randomUUID(), null).items()).isEmpty();
        var map = emotionQueryService.findMapWithinBounds(bounds, viewer.getPublicId(), group.getPublicId(), null);
        assertThat(map.items()).hasSize(21).allSatisfy(item -> {
            assertThat(item.groupId()).isEqualTo(group.getPublicId());
            assertThat(item.groupStamp().text()).isEqualTo(stamp.getText());
        });
        assertThat(map.items()).extracting(EmotionMapItemView::id)
                .containsExactlyElementsOf(Stream.concat(first.items().stream(), second.items().stream())
                        .map(EmotionDetailView::id).toList());
    }

    private static Stream<Arguments> emotionContents() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("  " + "😀".repeat(200) + "  ", null),
                Arguments.of(null, Audio.builder().objectKey("recordings/기기/음성 기록.m4a").build())
        );
    }

    @Test
    void 공개_목록은_기간과_총량_제한없이_커서로_이어지며_이모지를_포함한다() {
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0);
        for (int i = 0; i < 20; i++) {
            saveEmotion(author.getId(), 126.9774, 37.5669);
        }
        emotionCommandService.updateEmoji(emotion.getId(), viewer.getPublicId(), EmojiType.HEART, true);
        emotionCommandService.updateEmoji(emotion.getId(), author.getPublicId(), EmojiType.HEART, true);
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();

        EmotionPageView first = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        EmotionPageView second = emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, first.nextCursor());

        assertThat(first.items()).hasSize(20);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.items()).extracting(EmotionDetailView::id).doesNotContain(emotion.getId());
        assertThat(second.items()).extracting(EmotionDetailView::id).containsExactly(emotion.getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(second.items().getFirst().emojis()).hasSize(6).contains(EmotionEmojiResult.of(EmojiType.HEART, 2, true));
        Device other = deviceRepository.save(기본_기기_빌더().build());
        EmotionPageView otherViewer = emotionQueryService.findListWithinBounds(null, other.getPublicId(), null, first.nextCursor());
        assertThat(otherViewer.items().getFirst().emojis()).contains(EmotionEmojiResult.of(EmojiType.HEART, 2, false));
    }

    @Test
    void 공개_목록은_다음_페이지에서_차단과_신규_등록을_다시_반영한다() {
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0);
        for (int i = 0; i < 20; i++) {
            saveEmotion(author.getId(), 126.9774, 37.5669);
        }
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();
        EmotionPageView first = emotionQueryService.findListWithinBounds(bounds, viewer.getPublicId(), null, null);
        emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(emotion.getId()).build());
        Emotion newEmotion = saveEmotion(author.getId(), 126.9774, 37.5669);
        entityManager.flush();
        setCreatedAt(newEmotion, Instant.now().plusSeconds(10));

        EmotionPageView second = emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, first.nextCursor());
        assertThat(second.items()).isEmpty();
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 공개_목록은_변조된_커서와_미래_스냅샷을_거부한다() {
        assertThatThrownBy(() -> emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, "invalid"))
                .isInstanceOf(EmotionException.class);
        String future = EmotionCursorCodec.encode(EmotionCursor.initialWithinBounds(
                EmotionSearchBounds.of(126.0, 37.0, 128.0, 38.0), Instant.now().plusSeconds(60), null));
        assertThatThrownBy(() -> emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, future))
                .isInstanceOf(EmotionException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 49, 50, 51})
    void 좌표_없는_첫_페이지는_오래된_화면_밖_감정까지_정렬하여_최대_50개를_반환한다(int count) {
        // given
        emotion.delete();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ids.add(saveEmotion(author.getId(), index % 2 == 0 ? 127.0 : 129.0, 37.5).getId());
        }
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();

        List<Long> expected = new ArrayList<>(ids.reversed());
        if (!ids.isEmpty()) {
            jdbcClient.sql("UPDATE emotions SET created_at = '2024-01-01T00:00:00Z' WHERE id = :id")
                    .param("id", ids.getLast()).update();
            expected.add(expected.removeFirst());
        }
        entityManager.clear();

        // when
        EmotionPageView page = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null);

        // then
        assertThat(page.items()).extracting(EmotionDetailView::id)
                .containsExactlyElementsOf(expected.stream().limit(50).toList());
        assertThat(page.hasNext()).isEqualTo(count > 50);
        if (page.hasNext()) {
            EmotionCursor cursor = EmotionCursorCodec.decodeWithoutBounds(page.nextCursor());
            assertThat(cursor.bounds()).isNull();
            assertThat(cursor.groupId()).isNull();
            assertThat(cursor.lastId()).isEqualTo(page.items().getLast().id());
            assertThat(cursor.lastItemCreatedAt()).isEqualTo(page.items().getLast().createdAt());
        } else {
            assertThat(page.nextCursor()).isNull();
        }

        assertThat(emotionQueryService.findListWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId(), null, null).items()).hasSize(Math.min((count + 1) / 2, 20));
    }

    @Test
    void 좌표_없는_첫_페이지는_내용_삭제_차단_스냅샷_필터를_페이지_제한_전에_적용한다() {
        // given: 제외 대상이 최신 50개를 채워도 오래된 정상 감정으로 페이지를 채운다.
        emotion.delete();
        List<Long> visibleIds = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            visibleIds.add(saveEmotion(index == 0 ? null : author.getId(), 129, 35).getId());
        }
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();

        Device blockedAuthor = deviceRepository.save(기본_기기_빌더().build());
        Emotion origin = saveEmotion(blockedAuthor.getId(), 129, 35);
        deviceBlockRepository.save(DeviceBlock.builder().blockerDeviceId(viewer.getId())
                .blockedDeviceId(blockedAuthor.getId()).originEmotionId(origin.getId()).build());
        for (int index = 0; index < 51; index++) {
            emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).build());
            saveEmotion(author.getId(), 129, 35).delete();
            Emotion blocked = saveEmotion(author.getId(), 129, 35);
            emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId())
                    .emotionId(blocked.getId()).build());
            saveEmotion(blockedAuthor.getId(), 129, 35);
        }
        Emotion future = saveEmotion(author.getId(), 129, 35);
        entityManager.flush();
        setCreatedAt(future, Instant.now().plusSeconds(3_600));
        entityManager.clear();

        // when
        EmotionPageView page = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null);

        // then
        assertThat(page.items()).extracting(EmotionDetailView::id).containsExactlyElementsOf(visibleIds.reversed());
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.longitude()).isEqualTo(129);
            assertThat(item.latitude()).isEqualTo(35);
        });
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 51})
    void 좌표_없는_그룹_목록은_현재_멤버십과_무관하게_감정의_스탬프로_필터링한다(int count) {
        // given: 작성자와 조회 기기 모두 현재 그룹 멤버가 아니다.
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            expected.add(saveEmotionWithStamp(stamp).getId());
        }
        Emotion deleted = saveEmotionWithStamp(stamp);
        deleted.delete();
        Emotion blocked = saveEmotionWithStamp(stamp);
        emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId())
                .emotionId(blocked.getId()).build());

        Group otherGroup = 기본_그룹_빌더().name("다른 그룹").build();
        entityManager.persist(otherGroup);
        GroupStamp otherStamp = 기본_스탬프_빌더(otherGroup).build();
        entityManager.persist(otherStamp);
        for (int index = 0; index < 51; index++) {
            saveEmotionWithStamp(otherStamp);
        }
        Emotion withoutStamp = saveEmotion(author.getId(), 129, 35);
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();

        // when
        EmotionPageView page = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), group.getPublicId(), null);

        // then: 더 최신인 다른 그룹 감정이 50개를 넘어도 지정 그룹으로 페이지를 채운다.
        assertThat(page.items()).extracting(EmotionDetailView::id)
                .containsExactlyElementsOf(expected.reversed().stream().limit(50).toList());
        assertThat(page.items()).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(group.getPublicId()));
        assertThat(page.hasNext()).isEqualTo(count > 50);
        if (page.hasNext()) {
            assertThat(EmotionCursorCodec.decodeWithoutBounds(page.nextCursor()).groupId()).isEqualTo(group.getPublicId());
            EmotionPageView next = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, page.nextCursor());
            assertThat(next.items()).extracting(EmotionDetailView::id).containsExactly(expected.getFirst());
            assertThat(next.items()).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(group.getPublicId()));
            assertThat(next.hasNext()).isFalse();
            assertThat(next.nextCursor()).isNull();

            EmotionPageView nextWithGroup = emotionQueryService.findListWithoutBounds(
                    viewer.getPublicId(), group.getPublicId(), page.nextCursor());
            assertThat(nextWithGroup).isEqualTo(next);
        }

        EmotionPageView all = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null);
        assertThat(all.items()).extracting(EmotionDetailView::groupId).containsNull().contains(otherGroup.getPublicId());
        assertThat(all.items()).extracting(EmotionDetailView::id).contains(withoutStamp.getId());
        assertThat(emotionQueryService.findListWithoutBounds(viewer.getPublicId(), UUID.randomUUID(), null).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 좌표_없는_목록은_전체나_다른_그룹의_커서에_새_그룹_조건을_지정하면_거부한다(boolean cursorHasGroup) {
        // given
        UUID cursorGroupId = cursorHasGroup ? UUID.randomUUID() : null;
        String cursor = EmotionCursorCodec.encode(EmotionCursor.initialWithoutBounds(Instant.now(), cursorGroupId));
        UUID requestedGroupId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> emotionQueryService.findListWithoutBounds(viewer.getPublicId(), requestedGroupId, cursor))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_INVALID_CURSOR));
    }

    @Test
    void 그룹을_옮겨도_기존_커서는_작성_당시_그룹을_유지하고_새_조회는_선택한_그룹을_반환한다() {
        // given: 그룹 A에서 작성한 감정 51개를 비회원 기기가 조회한다.
        Group previousGroup = 기본_그룹_빌더().name("이전 그룹").build();
        entityManager.persist(previousGroup);
        GroupStamp previousStamp = 기본_스탬프_빌더(previousGroup).build();
        entityManager.persist(previousStamp);
        GroupMember previousMember = 일반_멤버_빌더(previousGroup, author).build();
        entityManager.persist(previousMember);
        List<Long> previousIds = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            previousIds.add(saveEmotionWithStamp(previousStamp).getId());
        }
        entityManager.flush();
        EmotionPageView first = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), previousGroup.getPublicId(), null);

        // when: 작성자가 A를 탈퇴하고 B에서 새 감정을 작성한다.
        previousMember.leave(Instant.now());
        Group currentGroup = 기본_그룹_빌더().name("현재 그룹").build();
        entityManager.persist(currentGroup);
        GroupStamp currentStamp = 기본_스탬프_빌더(currentGroup).build();
        entityManager.persist(currentStamp);
        entityManager.persist(일반_멤버_빌더(currentGroup, author).build());
        Emotion currentEmotion = saveEmotionWithStamp(currentStamp);
        entityManager.flush();
        entityManager.clear();

        EmotionPageView next = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, first.nextCursor());
        EmotionPageView changed = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), currentGroup.getPublicId(), null);

        // then
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionDetailView::id).toList())
                .containsExactlyElementsOf(previousIds.reversed());
        assertThat(next.items()).allSatisfy(item -> assertThat(item.groupId()).isEqualTo(previousGroup.getPublicId()));
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(changed.items()).extracting(EmotionDetailView::id).containsExactly(currentEmotion.getId());
        assertThat(changed.items().getFirst().groupId()).isEqualTo(currentGroup.getPublicId());
        assertThat(changed.hasNext()).isFalse();
    }

    @Test
    void 좌표_없는_목록도_존재하지_않는_기기는_조회할_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> emotionQueryService.findListWithoutBounds(UUID.randomUUID(), null, null))
                .isInstanceOf(DeviceException.class);
        String cursor = EmotionCursorCodec.encode(EmotionCursor.initialWithoutBounds(Instant.now(), null));
        assertThatThrownBy(() -> emotionQueryService.findListWithoutBounds(UUID.randomUUID(), null, cursor))
                .isInstanceOf(DeviceException.class);
    }

    @Test
    void 좌표_없는_목록은_동일_시각의_감정도_중복_누락_없이_이어지고_스냅샷을_유지한다() {
        // given
        List<Long> expected = new ArrayList<>(List.of(emotion.getId()));
        for (int index = 0; index < 100; index++) {
            expected.add(saveEmotion(author.getId(), 129, 35).getId());
        }
        emotionCommandService.updateEmoji(emotion.getId(), viewer.getPublicId(), EmojiType.HEART, true);
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();

        // when
        EmotionPageView first = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null);
        EmotionPageView second = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, first.nextCursor());
        EmotionPageView third = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, second.nextCursor());

        // then
        assertThat(first.items()).hasSize(50);
        assertThat(second.items()).hasSize(50);
        assertThat(third.items()).hasSize(1);
        assertThat(Stream.of(first, second, third).flatMap(page -> page.items().stream()).map(EmotionDetailView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        assertThat(EmotionCursorCodec.decodeWithoutBounds(second.nextCursor()).snapshotAt())
                .isEqualTo(EmotionCursorCodec.decodeWithoutBounds(first.nextCursor()).snapshotAt());
        assertThat(third.hasNext()).isFalse();
        assertThat(third.nextCursor()).isNull();
        assertThat(third.items().getFirst().emojis()).hasSize(6).contains(EmotionEmojiResult.of(EmojiType.HEART, 1, true));
    }

    @Test
    void 좌표_없는_다음_페이지는_새_감정을_제외하고_현재_삭제와_차단을_반영한다() {
        // given: 삭제·차단할 감정은 첫 페이지 다음에 위치한다.
        Emotion deleted = saveEmotion(author.getId(), 129, 35);
        Emotion blocked = saveEmotion(author.getId(), 129, 35);
        Device blockedAuthor = deviceRepository.save(기본_기기_빌더().build());
        Emotion byBlockedAuthor = saveEmotion(blockedAuthor.getId(), 129, 35);
        for (int index = 0; index < 50; index++) {
            saveEmotion(author.getId(), 129, 35);
        }
        entityManager.flush();
        jdbcClient.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        entityManager.clear();
        EmotionPageView first = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, null);

        entityManager.find(Emotion.class, deleted.getId()).delete();
        emotionBlockRepository.save(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        deviceBlockRepository.save(DeviceBlock.builder().blockerDeviceId(viewer.getId())
                .blockedDeviceId(blockedAuthor.getId()).originEmotionId(byBlockedAuthor.getId()).build());
        Emotion added = saveEmotion(author.getId(), 129, 35);
        entityManager.flush();
        Instant snapshotAt = EmotionCursorCodec.decodeWithoutBounds(first.nextCursor()).snapshotAt();
        setCreatedAt(added, snapshotAt.plusSeconds(1));
        entityManager.clear();

        // when
        EmotionPageView next = emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, first.nextCursor());
        EmotionPageView anotherViewer = emotionQueryService.findListWithoutBounds(author.getPublicId(), null, first.nextCursor());

        // then
        assertThat(next.items()).extracting(EmotionDetailView::id).containsExactly(emotion.getId());
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(anotherViewer.items()).extracting(EmotionDetailView::id)
                .containsExactly(byBlockedAuthor.getId(), blocked.getId(), emotion.getId());
    }

    @Test
    void 좌표_없는_다음_페이지는_잘못된_커서와_미래_스냅샷과_좌표_있는_커서를_거부한다() {
        // given
        Instant now = Instant.now();
        EmotionSearchBounds bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        List<String> invalidCursors = List.of("invalid",
                EmotionCursorCodec.encode(EmotionCursor.initialWithoutBounds(now.plusSeconds(60), null)),
                EmotionCursorCodec.encode(EmotionCursor.initialWithinBounds(bounds, now, null)),
                EmotionCursorCodec.encode(EmotionCursor.initialWithinBounds(bounds, now, UUID.randomUUID())));

        // when / then
        for (String cursor : invalidCursors) {
            assertThatThrownBy(() -> emotionQueryService.findListWithoutBounds(viewer.getPublicId(), null, cursor))
                    .isInstanceOfSatisfying(EmotionException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_INVALID_CURSOR));
        }
        String withoutBounds = EmotionCursorCodec.encode(EmotionCursor.initialWithoutBounds(now, null));
        assertThatThrownBy(() -> emotionQueryService.findListWithinBounds(null, viewer.getPublicId(), null, withoutBounds))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_INVALID_CURSOR));
    }

    private Emotion saveEmotionWithStamp(GroupStamp stamp) {
        return emotionRepository.save(기본_한숨_빌더()
                .state(EmotionState.FRUSTRATED)
                .memo("메모")
                .deviceId(author.getId())
                .groupStamp(stamp)
                .build());
    }

    private Emotion saveEmotion(Long authorId, double longitude, double latitude) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point location = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        return emotionRepository.save(기본_한숨_빌더()
                .location(location)
                .state(EmotionState.FRUSTRATED)
                .memo("메모")
                .deviceId(authorId)
                .build());
    }

    private void 과거_닉네임을_저장한다(Emotion target) {
        jdbcClient.sql("UPDATE emotions SET nickname = :nickname WHERE id = :id")
                .param("nickname", "먼지구름").param("id", target.getId()).update();
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
