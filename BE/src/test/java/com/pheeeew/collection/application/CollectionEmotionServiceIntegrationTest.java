package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.infra.CollectionEmotionCursorCodec;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionEmotionQueryService.class, CollectionEmotionCursorCodec.class})
class CollectionEmotionServiceIntegrationTest {

    @Autowired private CollectionEmotionQueryService service;
    @Autowired private DeviceRepository devices;
    @Autowired private CollectionCardRepository cards;
    @Autowired private EmotionRepository emotions;
    @Autowired private JdbcClient jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoBean private AudioUrlIssuer audioIssuer;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-04T12:00:00Z"));
    }

    @Test
    void 카드_날짜의_본인_감정을_내용_유무와_종류에_관계없이_KST_경계로_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2026, 11, 3)).build());
        var none = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-02T15:00:00Z");
        var memo = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.EXHAUSTED)
                .memo("지친 하루").build(), "2026-11-03T00:00:00Z");
        var audio = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.FRUSTRATED)
                .audio(Audio.builder().objectKey("private/recording").build()).build(), "2026-11-03T14:59:59.999999Z");
        감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-02T14:59:59.999999Z");
        감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-03T15:00:00Z");
        감정을_저장한다(기본_한숨_빌더().deviceId(other.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-03T00:00:00Z");
        var playback = PlaybackUrl.of("https://audio.example/recording", Instant.parse("2026-11-04T13:00:00Z"));
        when(audioIssuer.issuePlayback("private/recording")).thenReturn(playback);

        // when
        var result = service.findEmotions(owner.getPublicId(), card.getPublicId(), null);

        // then
        assertThat(result.items()).extracting(item -> item.id())
                .containsExactly(audio.getId(), memo.getId(), none.getId());
        assertThat(result.items().get(0).audio()).isEqualTo(playback);
        assertThat(result.items().get(1).memo()).isEqualTo("지친 하루");
        assertThat(result.items().get(2).memo()).isNull();
        assertThat(result.items().get(2).audio()).isNull();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 동시간_감정_50개_경계에서_중복없이_이어보고_추가등록은_새_조회에_반영한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2026, 11, 4)).build());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            ids.add(감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId())
                    .state(EmotionState.values()[i % 5]).build(), "2026-11-04T00:00:00Z").getId());
        }

        // when
        var first = service.findEmotions(owner.getPublicId(), card.getPublicId(), null);
        감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-04T12:00:01Z");
        when(clock.instant()).thenReturn(Instant.parse("2026-11-04T12:00:02Z"));
        var second = service.findEmotions(owner.getPublicId(), card.getPublicId(), first.nextCursor());
        var refreshed = service.findEmotions(owner.getPublicId(), card.getPublicId(), null);

        // then
        assertThat(first.items()).hasSize(50);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(first.items().getFirst().id()).isEqualTo(ids.getLast());
        assertThat(second.items()).extracting(item -> item.id()).containsExactly(ids.get(1), ids.getFirst());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(refreshed.items().getFirst().createdAt()).isEqualTo(Instant.parse("2026-11-04T12:00:01Z"));
        var combined = new ArrayList<>(first.items());
        combined.addAll(second.items());
        assertThat(combined).extracting(item -> item.id()).containsExactlyElementsOf(ids.reversed());
    }

    @Test
    void 커서는_다른_카드와_기기에_재사용하거나_원문과_서명을_변조할_수_없다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        var card = 페이지가_있는_카드를_저장한다(owner.getId());
        var otherDay = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2026, 11, 3)).combinationIndex(1).build());
        var otherDevice = cards.saveAndFlush(기본_카드_빌더().deviceId(other.getId())
                .cardDate(card.getCardDate()).build());
        String cursor = service.findEmotions(owner.getPublicId(), card.getPublicId(), null).nextCursor();
        String[] parts = cursor.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String changedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace(owner.getPublicId().toString(), other.getPublicId().toString()).getBytes(StandardCharsets.UTF_8));
        byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
        signature[0] ^= 1;
        String changedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        // when / then
        for (String invalid : List.of("", "invalid", "a".repeat(2049), parts[0],
                changedPayload + "." + parts[1], parts[0] + "." + changedSignature)) {
            assertThatThrownBy(() -> service.findEmotions(owner.getPublicId(), card.getPublicId(), invalid))
                    .isInstanceOf(CollectionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        }
        assertThatThrownBy(() -> service.findEmotions(owner.getPublicId(), otherDay.getPublicId(), cursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        assertThatThrownBy(() -> service.findEmotions(other.getPublicId(), otherDevice.getPublicId(), cursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        assertThatThrownBy(() -> service.findEmotions(other.getPublicId(), card.getPublicId(), cursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_CARD_NOT_FOUND);
        assertThatThrownBy(() -> service.findEmotions(owner.getPublicId(), UUID.randomUUID(), null))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_CARD_NOT_FOUND);
    }

    @Test
    void 다음_페이지에서도_삭제와_차단을_반영하고_감정이_없어져도_카드는_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2026, 11, 4)).build());
        var deleted = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId())
                .state(EmotionState.ANGRY).build(), "2026-11-04T00:00:00Z");
        var blocked = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId())
                .state(EmotionState.ANGRY).build(), "2026-11-04T00:00:00Z");
        for (int i = 0; i < 50; i++) {
            감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                    "2026-11-04T01:00:00Z");
        }
        var first = service.findEmotions(owner.getPublicId(), card.getPublicId(), null);
        deleted.delete();
        entityManager.persist(EmotionBlock.builder().blockerDeviceId(owner.getId()).emotionId(blocked.getId()).build());
        entityManager.flush();

        // when
        var second = service.findEmotions(owner.getPublicId(), card.getPublicId(), first.nextCursor());

        // then
        assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).isEmpty();
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 녹음_재생_URL_발급_실패는_기존_503_오류로_반환한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(LocalDate.of(2026, 11, 4)).build());
        감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY)
                .audio(Audio.builder().objectKey("private/unavailable").build()).build(), "2026-11-04T00:00:00Z");
        when(audioIssuer.issuePlayback("private/unavailable")).thenThrow(new IllegalStateException("발급 실패"));

        // when / then
        assertThatThrownBy(() -> service.findEmotions(owner.getPublicId(), card.getPublicId(), null))
                .isInstanceOf(EmotionException.class)
                .hasFieldOrPropertyWithValue("errorCode", EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE);
    }

    private Emotion 감정을_저장한다(Emotion emotion, String createdAt) {
        emotions.saveAndFlush(emotion);
        jdbc.sql("UPDATE emotions SET created_at = CAST(:at AS TIMESTAMPTZ) WHERE id = :id")
                .param("at", createdAt).param("id", emotion.getId()).update();
        entityManager.refresh(emotion);
        return emotion;
    }

    private CollectionCard 페이지가_있는_카드를_저장한다(Long deviceId) {
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(deviceId)
                .cardDate(LocalDate.of(2026, 11, 4)).build());
        for (int i = 0; i < 51; i++) {
            감정을_저장한다(기본_한숨_빌더().deviceId(deviceId).state(EmotionState.ANGRY).build(),
                    "2026-11-04T00:00:00Z");
        }
        return card;
    }
}
