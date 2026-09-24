package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer;
import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@PostgisDataJpaTest
@Import(EmotionQueryService.class)
class EmotionAudioPlaybackIntegrationTest {

    private static final String OBJECT_KEY = "recordings/private-voice.m4a";

    @Autowired
    private EmotionQueryService queryService;
    @Autowired
    private EmotionRepository emotionRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private EmotionBlockRepository emotionBlockRepository;
    @Autowired
    private DeviceBlockRepository deviceBlockRepository;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private AudioPlaybackUrlIssuer issuer;

    @MockitoSpyBean
    private EmotionEmojiRepository emojis;

    private Device viewer;
    private Device author;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        viewer = deviceRepository.save(기본_기기_빌더().build());
        author = deviceRepository.save(기본_기기_빌더().build());
        emotion = emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).memo(null)
                .audio(Audio.builder().objectKey(OBJECT_KEY).build()).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 상세를_다시_조회하면_재생_URL을_다시_발급하고_DB에는_객체_키만_유지한다() {
        // given
        PlaybackUrl first = PlaybackUrl.of("https://audio.example.test/first", Instant.now().plusSeconds(300));
        PlaybackUrl second = PlaybackUrl.of("https://audio.example.test/second", Instant.now().plusSeconds(600));
        when(issuer.issue(OBJECT_KEY)).thenReturn(first, second);

        // when / then
        assertThat(queryService.findById(emotion.getId(), viewer.getPublicId()).audio()).isEqualTo(first);
        assertThat(queryService.findById(emotion.getId(), viewer.getPublicId()).audio()).isEqualTo(second);
        entityManager.flush();
        entityManager.clear();
        assertThat(emotionRepository.findById(emotion.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo(OBJECT_KEY);
    }

    @Test
    void 녹음_목록과_메모_상세에서는_재생_URL을_발급하지_않는다() {
        // given
        Emotion memo = emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).memo("메모").build());
        entityManager.flush();

        // when / then
        var page = queryService.findFirstListPage(EmotionSearchBounds.of(126, 37, 128, 38), viewer.getPublicId());
        assertThat(page.items()).filteredOn(item -> item.id().equals(emotion.getId()))
                .singleElement().satisfies(item -> {
                    assertThat(item.hasAudio()).isTrue();
                    assertThat(item.audio()).isNull();
                });
        assertThat(queryService.findById(memo.getId(), viewer.getPublicId()).audio()).isNull();
        verifyNoInteractions(issuer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "emotionBlock", "deviceBlock", "missing"})
    void 조회할_수_없는_감정의_녹음은_발급하지_않는다(String reason) {
        // given
        long id = emotion.getId();
        switch (reason) {
            case "deleted" -> emotionRepository.findById(id).orElseThrow().delete();
            case "emotionBlock" -> emotionBlockRepository.save(EmotionBlock.builder()
                    .blockerDeviceId(viewer.getId()).emotionId(id).build());
            case "deviceBlock" -> deviceBlockRepository.save(DeviceBlock.builder()
                    .blockerDeviceId(viewer.getId()).blockedDeviceId(author.getId()).originEmotionId(id).build());
            case "missing" -> id = Long.MAX_VALUE;
        }
        entityManager.flush();
        long targetId = id;

        // when / then
        assertThatThrownBy(() -> queryService.findById(targetId, viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
        verifyNoInteractions(issuer);
    }

    @Test
    void 발급_실패는_원인을_보존한_서비스_오류로_반환한다() {
        // given
        RuntimeException failure = new IllegalStateException("signing failed");
        when(issuer.issue(OBJECT_KEY)).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> queryService.findById(emotion.getId(), viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE))
                .hasCause(failure);
        verify(issuer).issue(OBJECT_KEY);
    }

    @ParameterizedTest
    @MethodSource("invalidPlaybackUrls")
    void 유효하지_않거나_만료된_재생_URL은_반환하지_않는다(PlaybackUrl playbackUrl) {
        // given
        when(issuer.issue(OBJECT_KEY)).thenReturn(playbackUrl);

        // when / then
        assertThatThrownBy(() -> queryService.findById(emotion.getId(), viewer.getPublicId()))
                .isInstanceOfSatisfying(EmotionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE));
    }

    @Test
    void 지도는_200개씩_커서로_이어지며_이모지와_녹음_URL을_조회하지_않는다() {
        // given: 녹음 감정 한 개와 내용 없는 감정 200개
        for (int i = 0; i < 200; i++) {
            emotionRepository.save(기본_한숨_빌더().deviceId(author.getId()).build());
        }
        entityManager.flush();
        entityManager.clear();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);

        // when
        var first = queryService.findFirstMapPage(bounds, viewer.getPublicId(), null);
        var second = queryService.findNextMapPage(first.nextCursor(), viewer.getPublicId());

        // then
        assertThat(first.items()).hasSize(200);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).extracting(EmotionMapItemView::id).containsExactly(emotion.getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(first.items()).extracting(EmotionMapItemView::id).doesNotContain(emotion.getId());
        verifyNoInteractions(issuer, emojis);
    }

    private static Stream<PlaybackUrl> invalidPlaybackUrls() {
        return Stream.of(null, PlaybackUrl.of(" ", Instant.now().plusSeconds(300)),
                PlaybackUrl.of("https://audio.example.test/signed", null),
                PlaybackUrl.of("https://audio.example.test/signed", Instant.EPOCH));
    }
}
