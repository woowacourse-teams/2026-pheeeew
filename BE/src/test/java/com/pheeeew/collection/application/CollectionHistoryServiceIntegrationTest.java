package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionQueryService.class, CollectionProgressRepository.class,
        CollectionAssetConfiguration.class, CollectionAssetRepository.class})
@TestPropertySource(properties = {"pheeeew.s3.bucket=collection-test-assets", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.s3.key-prefix=pheeeew/development/",
        "pheeeew.collection.card-version=v1"})
class CollectionHistoryServiceIntegrationTest {

    @Autowired
    private CollectionQueryService service;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private CollectionCardRepository cards;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
    }

    @Test
    void 활동이_없으면_빈_이력을_반환한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());

        // when
        var result = service.findHistory(owner.getPublicId(), null, null, null);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 본인의_지난_카드만_상세와_동일한_내용으로_최신순_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        var older = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 20), EmotionState.ANGRY, 0);
        var newer = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.EXHAUSTED, 37);
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 1);
        카드를_저장한다(other.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 0);

        // when
        var result = service.findHistory(owner.getPublicId(), null, null, null);

        // then
        assertThat(result.items()).containsExactly(service.findDetail(owner.getPublicId(), newer.getPublicId()),
                service.findDetail(owner.getPublicId(), older.getPublicId()));
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 같은_날짜의_여러_카드도_중복_없이_다음_페이지로_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var date = LocalDate.of(2026, 10, 21);
        var first = 카드를_저장한다(owner.getId(), date, EmotionState.ANGRY, 0);
        var second = 카드를_저장한다(owner.getId(), date, EmotionState.EXHAUSTED, 0);
        var third = 카드를_저장한다(owner.getId(), date, EmotionState.FRUSTRATED, 0);

        // when
        var page = service.findHistory(owner.getPublicId(), null, 2, null);

        // then
        assertThat(page.items()).extracting(item -> item.cardId())
                .containsExactly(third.getPublicId(), second.getPublicId());
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        var lastPage = service.findHistory(owner.getPublicId(), null, 2, page.nextCursor());
        assertThat(lastPage.items()).extracting(item -> item.cardId()).containsExactly(first.getPublicId());
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.nextCursor()).isNull();
    }

    @Test
    void 감정_필터는_다음_페이지에서_생략해도_유지한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var date = LocalDate.of(2026, 10, 21);
        var older = 카드를_저장한다(owner.getId(), date.minusDays(1), EmotionState.ANGRY, 0);
        var newer = 카드를_저장한다(owner.getId(), date, EmotionState.ANGRY, 1);
        카드를_저장한다(owner.getId(), date, EmotionState.EXHAUSTED, 0);

        // when
        var first = service.findHistory(owner.getPublicId(), EmotionState.ANGRY, 1, null);
        var next = service.findHistory(owner.getPublicId(), null, 1, first.nextCursor());

        // then
        assertThat(first.items()).extracting(item -> item.cardId()).containsExactly(newer.getPublicId());
        assertThat(next.items()).extracting(item -> item.cardId()).containsExactly(older.getPublicId());
    }

    @Test
    void 커서와_다른_감정_필터는_거절한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 0);
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 20), EmotionState.ANGRY, 1);
        var page = service.findHistory(owner.getPublicId(), EmotionState.ANGRY, 1, null);

        // when / then
        assertThatThrownBy(() -> service.findHistory(owner.getPublicId(), EmotionState.EXHAUSTED, 1, page.nextCursor()))
                .isInstanceOfSatisfying(CollectionException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("COLLECTION-007"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 51, 2147483647})
    void 잘못된_페이지_크기는_거절한다(int limit) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());

        // when / then
        assertThatThrownBy(() -> service.findHistory(owner.getPublicId(), null, limit, null))
                .isInstanceOfSatisfying(CollectionException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("COLLECTION-008"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-cursor", "1|2026-10-22||2026-10-21|1|extra",
            "2|2026-10-22||2026-10-21|1", "1|2026-10-22|UNKNOWN|2026-10-21|1",
            "1|2026-10-23||2026-10-21|1", "1|2026-10-22||2026-10-22|1",
            "1|2026-10-22||2026-10-21|0", "1|2026-10-22||2026-10-21|-1",
            "1|2026-10-22||2026-02-30|1", "1|2026-10-22||0000-01-01|1"})
    void 잘못된_커서는_거절한다(String payload) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // when / then
        assertThatThrownBy(() -> service.findHistory(owner.getPublicId(), null, 20, cursor))
                .isInstanceOfSatisfying(CollectionException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo("COLLECTION-007"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void 반환할_카드의_집계가_누락되면_상세와_같은_오류를_반환한다(boolean stamp, boolean press) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var date = LocalDate.of(2026, 10, 21);
        cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(date).build());
        if (stamp) {
            일별_스탬프를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 1);
        }
        if (press) {
            개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 100);
        }

        // when / then
        assertThatThrownBy(() -> service.findHistory(owner.getPublicId(), null, 20, null))
                .isInstanceOfSatisfying(CollectionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_ACTIVITY_COUNTS_MISSING));
    }

    @Test
    void 다음_페이지_확인용_카드의_집계_누락은_현재_페이지를_실패시키지_않는다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var valid = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 0);
        cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(LocalDate.of(2026, 10, 20))
                .combinationIndex(1).build());

        // when
        var page = service.findHistory(owner.getPublicId(), null, 1, null);

        // then
        assertThat(page.items()).extracting(item -> item.cardId()).containsExactly(valid.getPublicId());
        assertThat(page.hasNext()).isTrue();
        assertThatThrownBy(() -> service.findHistory(owner.getPublicId(), null, 1, page.nextCursor()))
                .isInstanceOfSatisfying(CollectionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_ACTIVITY_COUNTS_MISSING));
    }

    @Test
    void 기본_20개와_최대_50개를_조회하고_마지막_페이지를_구분한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        for (int i = 0; i < 51; i++) {
            카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21).minusDays(i), EmotionState.ANGRY, i);
        }

        // when
        var defaultPage = service.findHistory(owner.getPublicId(), null, null, null);
        var maximumPage = service.findHistory(owner.getPublicId(), null, 50, null);
        var lastPage = service.findHistory(owner.getPublicId(), null, 50, maximumPage.nextCursor());

        // then
        assertThat(defaultPage.items()).hasSize(20);
        assertThat(defaultPage.hasNext()).isTrue();
        assertThat(maximumPage.items()).hasSize(50);
        assertThat(maximumPage.hasNext()).isTrue();
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void 자정_이후에도_커서의_날짜_범위는_유지하고_새로고침하면_갱신한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var old = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 20), EmotionState.ANGRY, 0);
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 1);
        var today = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 2);
        var first = service.findHistory(owner.getPublicId(), null, 1, null);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T15:00:00Z"));

        // when
        var next = service.findHistory(owner.getPublicId(), null, 20, first.nextCursor());
        var refreshed = service.findHistory(owner.getPublicId(), null, 20, null);

        // then
        assertThat(next.items()).extracting(item -> item.cardId()).containsExactly(old.getPublicId());
        assertThat(refreshed.items().getFirst().cardId()).isEqualTo(today.getPublicId());
    }

    @Test
    void 페이지_사이_추가된_카드는_커서_뒤면_보이고_앞이면_새로고침으로_확인한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var date = LocalDate.of(2026, 10, 21);
        var old = 카드를_저장한다(owner.getId(), date.minusDays(2), EmotionState.ANGRY, 0);
        var firstCard = 카드를_저장한다(owner.getId(), date, EmotionState.ANGRY, 1);
        var first = service.findHistory(owner.getPublicId(), null, 1, null);
        var ahead = 카드를_저장한다(owner.getId(), date, EmotionState.EXHAUSTED, 0);
        var behind = 카드를_저장한다(owner.getId(), date.minusDays(1), EmotionState.ANGRY, 2);

        // when
        var next = service.findHistory(owner.getPublicId(), null, 20, first.nextCursor());
        var refreshed = service.findHistory(owner.getPublicId(), null, 20, null);

        // then
        assertThat(next.items()).extracting(item -> item.cardId()).containsExactly(behind.getPublicId(), old.getPublicId());
        assertThat(refreshed.items()).extracting(item -> item.cardId())
                .containsExactly(ahead.getPublicId(), firstCard.getPublicId(), behind.getPublicId(), old.getPublicId());
    }

    @Test
    void 다시_조회하면_최신_집계를_반영하고_카드_조합은_유지한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var card = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 37);
        var before = service.findHistory(owner.getPublicId(), null, 20, null).items().getFirst();
        개인_프레스를_저장한다(jdbc, owner.getId(), card.getCardDate(), EmotionState.ANGRY, 5);

        // when
        var after = service.findHistory(owner.getPublicId(), null, 20, null).items().getFirst();

        // then
        assertThat(before.pressCount()).isEqualTo(125);
        assertThat(after.pressCount()).isEqualTo(130);
        assertThat(after.cardId()).isEqualTo(before.cardId());
        assertThat(after.images()).isEqualTo(before.images());
    }

    @Test
    void 존재하지_않는_기기는_거절한다() {
        assertThatThrownBy(() -> service.findHistory(UUID.randomUUID(), null, 20, null))
                .isInstanceOfSatisfying(DeviceException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    private CollectionCard 카드를_저장한다(
            Long deviceId, LocalDate date, EmotionState state, int combination) {
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(deviceId).cardDate(date)
                .state(state).combinationIndex(combination).build());
        일별_스탬프를_저장한다(jdbc, deviceId, date, state, 3);
        개인_프레스를_저장한다(jdbc, deviceId, date, state, 125);
        return card;
    }
}
