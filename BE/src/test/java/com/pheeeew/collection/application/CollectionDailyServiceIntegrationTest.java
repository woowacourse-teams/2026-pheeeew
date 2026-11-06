package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.infra.jwt.JwtProperties;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.collection.infra.CollectionEmotionCursorCodec;
import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionDailyQueryService.class, CollectionEmotionQueryService.class, CollectionEmotionCursorCodec.class,
        CollectionProgressRepository.class, CollectionAssetConfiguration.class, CollectionAssetRepository.class})
@TestPropertySource(properties = {"pheeeew.s3.bucket=collection-test-assets", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.s3.key-prefix=pheeeew/development/", "pheeeew.collection.card-version=v1"})
class CollectionDailyServiceIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    @Autowired private CollectionDailyQueryService service;
    @Autowired private CollectionEmotionQueryService cardEmotions;
    @Autowired private DeviceRepository devices;
    @Autowired private CollectionCardRepository cards;
    @Autowired private EmotionRepository emotions;
    @Autowired private EmotionEmojiRepository emojis;
    @Autowired private JdbcClient jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtProperties jwtProperties;
    @MockitoBean private Clock clock;
    @MockitoBean private AudioUrlIssuer audioIssuer;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-06T03:00:00Z"));
    }

    @Test
    void 날짜_카드_커서는_교차_사용할_수_없고_기존_카드_커서_형식은_계속_허용한다() throws Exception {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(DATE).build());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            ids.add(감정을_저장한다(owner, "2026-11-05T00:00:00Z").getId());
        }
        String dailyCursor = service.findDaily(owner.getPublicId(), DATE, null).emotions().nextCursor();
        String legacyPayload = String.join("|", "collection-emotions-1", owner.getPublicId().toString(),
                card.getPublicId().toString(), "2026-11-05", "2026-11-06T03:00:00Z", "2026-11-05T00:00:00Z",
                ids.get(2).toString());
        String legacyCursor = 기존_형식으로_서명한다(legacyPayload);

        // when / then
        var oldPage = cardEmotions.findEmotions(owner.getPublicId(), card.getPublicId(), legacyCursor);
        assertThat(oldPage.items()).extracting(item -> item.id()).containsExactly(ids.get(1), ids.getFirst());
        assertThatThrownBy(() -> service.findDaily(owner.getPublicId(), DATE, legacyCursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        assertThatThrownBy(() -> cardEmotions.findEmotions(owner.getPublicId(), card.getPublicId(), dailyCursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        assertThatThrownBy(() -> service.findDaily(owner.getPublicId(), DATE.minusDays(1), dailyCursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        assertThatThrownBy(() -> service.findDaily(other.getPublicId(), DATE, dailyCursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);

        String[] parts = dailyCursor.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace(owner.getPublicId().toString(), other.getPublicId().toString()).getBytes(StandardCharsets.UTF_8));
        byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
        signature[0] ^= 1;
        for (String invalid : List.of("", "invalid", "a".repeat(2049), dailyCursor + ".",
                tampered + "." + parts[1], parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature))) {
            assertThatThrownBy(() -> service.findDaily(owner.getPublicId(), DATE, invalid))
                    .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
        }
        when(clock.instant()).thenReturn(Instant.parse("2026-11-06T02:59:59Z"));
        assertThatThrownBy(() -> service.findDaily(owner.getPublicId(), DATE, dailyCursor))
                .hasFieldOrPropertyWithValue("errorCode", CollectionErrorCode.COLLECTION_INVALID_CURSOR);
    }

    @Test
    void 후속_페이지에서도_카드_발급과_프레스_공감은_갱신하고_새_감정은_새로고침에_반영한다() {
        // given
        when(clock.instant()).thenReturn(Instant.parse("2026-11-05T10:00:00Z"));
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            ids.add(감정을_저장한다(owner, "2026-11-05T00:00:00Z").getId());
        }
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.ANGRY, 1);
        var first = service.findDaily(owner.getPublicId(), DATE, null);
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(DATE)
                .claimedAt(clock.instant()).build());
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.EXHAUSTED, 9);
        emojis.saveIfAbsent(ids.get(1), owner.getId(), "HEART", clock.instant());
        var added = 감정을_저장한다(owner, "2026-11-05T10:00:01Z");
        when(clock.instant()).thenReturn(Instant.parse("2026-11-05T10:00:02Z"));

        // when
        var second = service.findDaily(owner.getPublicId(), DATE, first.emotions().nextCursor());
        var refreshed = service.findDaily(owner.getPublicId(), DATE, null);

        // then
        assertThat(first.card()).isNull();
        assertThat(first.pressCount()).isEqualTo(1);
        assertThat(second.card().cardId()).isEqualTo(card.getPublicId());
        assertThat(second.card().pressCount()).isEqualTo(10);
        assertThat(second.pressCount()).isEqualTo(10);
        assertThat(second.emotions().items()).extracting(item -> item.id()).containsExactly(ids.get(1), ids.getFirst());
        assertThat(second.emotions().items().getFirst().emojis().getFirst().count()).isEqualTo(1);
        assertThat(second.emotions().items().getFirst().emojis().getFirst().selected()).isTrue();
        assertThat(refreshed.emotions().items().getFirst().id()).isEqualTo(added.getId());

        // when / then
        emojis.deleteSelection(ids.get(1), owner.getId(), "HEART");
        var unselected = service.findDaily(owner.getPublicId(), DATE, first.emotions().nextCursor());
        assertThat(unselected.emotions().items().getFirst().emojis().getFirst().count()).isZero();
        assertThat(unselected.emotions().items().getFirst().emojis().getFirst().selected()).isFalse();
    }

    @Test
    void 날짜_상세도_KST_경계와_본인_삭제_차단_필터를_지키고_모든_감정과_내용을_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        var none = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(),
                "2026-11-04T15:00:00Z");
        var memo = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.EXHAUSTED)
                .memo("메모").build(), "2026-11-05T01:00:00Z");
        var irritated = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.IRRITATED).build(),
                "2026-11-05T02:00:00Z");
        var discouraged = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.DISCOURAGED).build(),
                "2026-11-05T03:00:00Z");
        var audio = 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.FRUSTRATED)
                .audio(Audio.builder().objectKey("private/daily-recording").build()).build(), "2026-11-05T14:59:59.999999Z");
        감정을_저장한다(owner, "2026-11-04T14:59:59.999999Z");
        감정을_저장한다(owner, "2026-11-05T15:00:00Z");
        감정을_저장한다(other, "2026-11-05T00:00:00Z");
        var deleted = 감정을_저장한다(owner, "2026-11-05T04:00:00Z");
        deleted.delete();
        var blocked = 감정을_저장한다(owner, "2026-11-05T05:00:00Z");
        entityManager.persist(EmotionBlock.builder().blockerDeviceId(owner.getId()).emotionId(blocked.getId()).build());
        entityManager.flush();
        var playback = AudioUrlIssuer.PlaybackUrl.of("https://audio.example/daily", Instant.parse("2026-11-06T04:00:00Z"));
        when(audioIssuer.issuePlayback("private/daily-recording")).thenReturn(playback);

        // when
        var result = service.findDaily(owner.getPublicId(), DATE, null);

        // then
        assertThat(result.card()).isNull();
        assertThat(result.emotions().items()).extracting(item -> item.id())
                .containsExactly(audio.getId(), discouraged.getId(), irritated.getId(), memo.getId(), none.getId());
        assertThat(result.emotions().items().getFirst().audio()).isEqualTo(playback);
        assertThat(result.emotions().items().get(3).memo()).isEqualTo("메모");
        assertThat(result.emotions().items()).allSatisfy(item -> assertThat(item.emojis()).hasSize(6));
    }

    @Test
    void 발급된_카드와_최상위에는_카드_감정에_상관없이_동일한_프레스_합계를_반환한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(DATE).state(EmotionState.IRRITATED).build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(other.getId()).cardDate(DATE).build());
        일별_스탬프를_저장한다(jdbc, owner.getId(), DATE, EmotionState.ANGRY, 2);
        일별_스탬프를_저장한다(jdbc, owner.getId(), DATE, EmotionState.EXHAUSTED, 3);
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.ANGRY, 40);
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.EXHAUSTED, 70);
        개인_프레스를_저장한다(jdbc, other.getId(), DATE, EmotionState.ANGRY, 999);

        // when
        var result = service.findDaily(owner.getPublicId(), DATE, null);

        // then
        assertThat(result.card().cardId()).isEqualTo(card.getPublicId());
        assertThat(result.card().date()).isEqualTo(DATE);
        assertThat(result.card().state()).isEqualTo(EmotionState.IRRITATED);
        assertThat(result.card().stampCount()).isEqualTo(5);
        assertThat(result.card().pressCount()).isEqualTo(110);
        assertThat(result.pressCount()).isEqualTo(110);
        assertThat(result.card().images().characterUrl()).contains("/irritated/");
        assertThat(result.emotions().items()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,false", "7,false", "0,true", "7,true"})
    void 오래된_미발급_날짜의_감정과_프레스가_있거나_없어도_정상_조회한다(long presses, boolean hasEmotion) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate oldDate = LocalDate.of(2024, 2, 29);
        if (presses > 0) {
            개인_프레스를_저장한다(jdbc, owner.getId(), oldDate, EmotionState.ANGRY, presses);
        }
        if (hasEmotion) {
            감정을_저장한다(owner, "2024-02-29T03:00:00Z");
        }

        // when
        var result = service.findDaily(owner.getPublicId(), oldDate, null);

        // then
        assertThat(result.date()).isEqualTo(oldDate);
        assertThat(result.card()).isNull();
        assertThat(result.pressCount()).isEqualTo(presses);
        assertThat(result.emotions().items()).hasSize(hasEmotion ? 1 : 0);
        assertThat(result.emotions().hasNext()).isFalse();
        assertThat(result.emotions().nextCursor()).isNull();
    }

    @Test
    void 미래_날짜는_KST_자정_전까지_거절하고_그_날짜가_오늘이_되면_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate nextDay = LocalDate.of(2026, 11, 7);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-06T14:59:59.999999Z"));

        // when / then
        assertThatThrownBy(() -> service.findDaily(owner.getPublicId(), nextDay, null))
                .isInstanceOf(PheeeewException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);

        // when
        when(clock.instant()).thenReturn(Instant.parse("2026-11-06T15:00:00Z"));
        var today = service.findDaily(owner.getPublicId(), nextDay, null);

        // then
        assertThat(today.date()).isEqualTo(nextDay);
        assertThat(today.card()).isNull();
        assertThat(today.pressCount()).isZero();
        assertThat(today.emotions().items()).isEmpty();
    }

    @Test
    void 카드가_없어도_본인_날짜의_프레스_합계와_공감이_포함된_감정을_페이지로_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            ids.add(감정을_저장한다(owner, "2026-11-05T00:00:00Z").getId());
        }
        감정을_저장한다(other, "2026-11-05T00:00:00Z");
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.ANGRY, 40);
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE, EmotionState.EXHAUSTED, 70);
        개인_프레스를_저장한다(jdbc, other.getId(), DATE, EmotionState.ANGRY, 999);
        개인_프레스를_저장한다(jdbc, owner.getId(), DATE.minusDays(1), EmotionState.ANGRY, 999);
        emojis.saveIfAbsent(ids.getLast(), other.getId(), "HEART", clock.instant());

        // when
        var first = service.findDaily(owner.getPublicId(), DATE, null);
        var second = service.findDaily(owner.getPublicId(), DATE, first.emotions().nextCursor());

        // then
        assertThat(first.date()).isEqualTo(DATE);
        assertThat(first.card()).isNull();
        assertThat(first.pressCount()).isEqualTo(110);
        assertThat(first.emotions().items()).hasSize(50);
        assertThat(first.emotions().hasNext()).isTrue();
        assertThat(first.emotions().items().getFirst().emojis()).hasSize(6);
        assertThat(first.emotions().items().getFirst().emojis().getFirst().count()).isEqualTo(1);
        assertThat(first.emotions().items().getFirst().emojis().getFirst().selected()).isFalse();
        assertThat(second.card()).isNull();
        assertThat(second.pressCount()).isEqualTo(110);
        assertThat(second.emotions().items()).extracting(item -> item.id()).containsExactly(ids.get(1), ids.getFirst());
        assertThat(second.emotions().hasNext()).isFalse();
        assertThat(second.emotions().nextCursor()).isNull();
    }

    private Emotion 감정을_저장한다(Device owner, String createdAt) {
        return 감정을_저장한다(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.ANGRY).build(), createdAt);
    }

    private Emotion 감정을_저장한다(Emotion emotion, String createdAt) {
        emotions.saveAndFlush(emotion);
        jdbc.sql("UPDATE emotions SET created_at = CAST(:at AS TIMESTAMPTZ) WHERE id = :id")
                .param("at", createdAt).param("id", emotion.getId()).update();
        entityManager.refresh(emotion);
        return emotion;
    }

    private String 기존_형식으로_서명한다(String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(jwtProperties.privateKey());
        signer.update(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
}
