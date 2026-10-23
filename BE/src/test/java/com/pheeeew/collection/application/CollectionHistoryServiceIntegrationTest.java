package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.application.dto.CollectionHistoryResult;
import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.domain.repository.DeviceDailyStampRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeviceDailyStampRepository stamps;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
    }

    @Test
    void 조건을_달성해도_수령_카드가_없으면_목록과_개수는_비어있다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());

        일별_스탬프를_저장한다(jdbc, owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 100);

        // when
        var result = service.findHistory(owner.getPublicId(), null, null, null);

        // then
        assertThat(result.nickname()).isNull();
        assertThat(result.counts().total()).isZero();
        assertThat(result.counts().byEmotion()).isEqualTo(Map.of(
                EmotionState.ANGRY, 0L, EmotionState.EXHAUSTED, 0L,
                EmotionState.FRUSTRATED, 0L, EmotionState.IRRITATED, 0L, EmotionState.DISCOURAGED, 0L));
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 오늘까지_본인의_수령_카드를_상세와_동일하게_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        var older = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 20), EmotionState.ANGRY, 0);
        var newer = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.EXHAUSTED, 37);
        var today = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 1);
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 2);
        카드를_저장한다(other.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 0);

        // when
        var result = service.findHistory(owner.getPublicId(), null, null, null);

        // then
        assertThat(result.counts().total()).isEqualTo(3);
        assertThat(result.counts().byEmotion()).isEqualTo(Map.of(
                EmotionState.ANGRY, 2L, EmotionState.EXHAUSTED, 1L,
                EmotionState.FRUSTRATED, 0L, EmotionState.IRRITATED, 0L, EmotionState.DISCOURAGED, 0L));
        assertThat(result.items()).containsExactly(service.findDetail(owner.getPublicId(), today.getPublicId()),
                service.findDetail(owner.getPublicId(), newer.getPublicId()),
                service.findDetail(owner.getPublicId(), older.getPublicId()));
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 카드_발급_당시가_아닌_현재_기기의_닉네임을_반환한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().nickname("처음이름").build());
        devices.saveAndFlush(기본_기기_빌더().nickname("다른기기").build());
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 0);
        var before = service.findHistory(owner.getPublicId(), null, 20, null);
        owner.updateNickname("바뀐이름");
        devices.saveAndFlush(owner);

        // when
        var after = service.findHistory(owner.getPublicId(), null, 20, null);
        var empty = service.findHistory(owner.getPublicId(), EmotionState.EXHAUSTED, 20, null);

        // then
        assertThat(before.nickname()).isEqualTo("처음이름");
        assertThat(after.nickname()).isEqualTo("바뀐이름");
        assertThat(after.items()).isEqualTo(before.items());
        assertThat(after.counts()).isEqualTo(before.counts());
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nickname()).isEqualTo("바뀐이름");
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
        assertThat(first.counts().total()).isEqualTo(3);
        assertThat(first.counts().byEmotion()).containsEntry(EmotionState.ANGRY, 2L)
                .containsEntry(EmotionState.EXHAUSTED, 1L);
        assertThat(next.counts()).isEqualTo(first.counts());
        var empty = service.findHistory(owner.getPublicId(), EmotionState.IRRITATED, 20, null);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(empty.nextCursor()).isNull();
        assertThat(empty.counts()).isEqualTo(first.counts());
    }

    @Test
    void 다섯_감정의_개수를_구분하고_마지막_페이지_이후에도_전체_개수를_유지한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var date = LocalDate.of(2026, 10, 22);
        var expected = Map.of(EmotionState.FRUSTRATED, 1L, EmotionState.IRRITATED, 2L,
                EmotionState.EXHAUSTED, 3L, EmotionState.DISCOURAGED, 4L, EmotionState.ANGRY, 5L);
        expected.forEach((state, count) -> {
            for (int i = 0; i < count; i++) {
                카드를_저장한다(owner.getId(), date.minusDays(i), state, i);
            }
        });

        // when
        var result = service.findHistory(owner.getPublicId(), null, 20, null);
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "1|2026-10-23||2026-10-17|1".getBytes(StandardCharsets.UTF_8));
        var empty = service.findHistory(owner.getPublicId(), null, 20, cursor);

        // then
        assertThat(result.items()).hasSize(15);
        assertThat(result.counts().total()).isEqualTo(15);
        assertThat(result.counts().byEmotion()).isEqualTo(expected);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.counts()).isEqualTo(result.counts());
    }

    @Test
    void 기존_커서의_오늘_제외_범위는_목록과_개수에서_유지한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var old = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 20), EmotionState.ANGRY, 0);
        var last = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 1);
        카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 2);
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1|2026-10-22||2026-10-21|" + last.getId()).getBytes(StandardCharsets.UTF_8));

        // when
        var result = service.findHistory(owner.getPublicId(), null, 20, cursor);

        // then
        assertThat(result.items()).extracting(item -> item.cardId()).containsExactly(old.getPublicId());
        assertThat(result.counts().total()).isEqualTo(2);
        assertThat(result.counts().byEmotion()).containsEntry(EmotionState.ANGRY, 2L);
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
            "1|2026-10-24||2026-10-21|1", "1|2026-10-22||2026-10-22|1",
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
        var yesterday = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 1);
        var today = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 2);
        var first = service.findHistory(owner.getPublicId(), null, 1, null);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T15:00:00Z"));
        var tomorrow = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 3);

        // when
        var next = service.findHistory(owner.getPublicId(), null, 20, first.nextCursor());
        var refreshed = service.findHistory(owner.getPublicId(), null, 20, null);

        // then
        assertThat(first.items()).extracting(item -> item.cardId()).containsExactly(today.getPublicId());
        assertThat(next.items()).extracting(item -> item.cardId())
                .containsExactly(yesterday.getPublicId(), old.getPublicId());
        assertThat(first.counts().total()).isEqualTo(3);
        assertThat(next.counts().total()).isEqualTo(3);
        assertThat(refreshed.items().getFirst().cardId()).isEqualTo(tomorrow.getPublicId());
        assertThat(refreshed.counts().total()).isEqualTo(4);
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 미커밋_카드를_기다리지_않고_커밋_후_커서_위치에_따라_노출한다(boolean behindCursor) throws Exception {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var firstCard = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 0);
            var oldest = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 19), EmotionState.ANGRY, 1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var pending = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    var date = LocalDate.of(2026, 10, behindCursor ? 20 : 21);
                    var card = 카드를_저장한다(owner.getId(), date, EmotionState.EXHAUSTED, 0);
                    written.countDown();
                    커밋_허용을_기다린다(release);
                    return card.getPublicId();
                }));
                CollectionHistoryResult first;
                try {
                    assertThat(written.await(5, TimeUnit.SECONDS)).isTrue();

                    // when / then: 미커밋 카드가 있어도 읽기는 완료된다.
                    first = executor.submit(() -> service.findHistory(owner.getPublicId(), null, 1, null))
                            .get(5, TimeUnit.SECONDS);
                    assertThat(first.items()).extracting(item -> item.cardId()).containsExactly(firstCard.getPublicId());
                    assertThat(first.hasNext()).isTrue();
                    assertThat(first.counts().total()).isEqualTo(2);
                } finally {
                    release.countDown();
                }
                UUID committedId = pending.get(5, TimeUnit.SECONDS);
                var next = service.findHistory(owner.getPublicId(), null, 20, first.nextCursor());
                assertThat(next.counts().total()).isEqualTo(3);
                if (behindCursor) {
                    assertThat(next.items()).extracting(item -> item.cardId()).containsExactly(committedId, oldest.getPublicId());
                } else {
                    assertThat(next.items()).extracting(item -> item.cardId()).containsExactly(oldest.getPublicId());
                }
                assertThat(service.findHistory(owner.getPublicId(), null, 20, null).items())
                        .extracting(item -> item.cardId()).contains(committedId);
            }
        } finally {
            release.countDown();
            기기_테스트_데이터를_삭제한다(owner.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 미커밋_집계_변경을_기다리지_않고_커밋_후_최신_횟수를_조회한다() throws Exception {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var card = 카드를_저장한다(owner.getId(), LocalDate.of(2026, 10, 21), EmotionState.ANGRY, 37);
            var original = service.findHistory(owner.getPublicId(), null, 20, null).items().getFirst();
            try (var executor = Executors.newFixedThreadPool(2)) {
                var pending = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    stamps.increase(card.getCardDate(), EmotionState.ANGRY, owner.getId(), clock.instant());
                    개인_프레스를_저장한다(jdbc, owner.getId(), card.getCardDate(), EmotionState.ANGRY, 5);
                    written.countDown();
                    커밋_허용을_기다린다(release);
                    return null;
                }));
                try {
                    assertThat(written.await(5, TimeUnit.SECONDS)).isTrue();

                    // when / then
                    var before = executor.submit(() -> service.findHistory(owner.getPublicId(), null, 20, null))
                            .get(5, TimeUnit.SECONDS).items().getFirst();
                    assertThat(before.stampCount()).isEqualTo(3);
                    assertThat(before.pressCount()).isEqualTo(125);
                } finally {
                    release.countDown();
                }
                pending.get(5, TimeUnit.SECONDS);
            }
            var after = service.findHistory(owner.getPublicId(), null, 20, null).items().getFirst();
            assertThat(after.stampCount()).isEqualTo(4);
            assertThat(after.pressCount()).isEqualTo(130);
            assertThat(after.cardId()).isEqualTo(original.cardId());
            assertThat(after.date()).isEqualTo(original.date());
            assertThat(after.images()).isEqualTo(original.images());
        } finally {
            release.countDown();
            기기_테스트_데이터를_삭제한다(owner.getId());
        }
    }

    private CollectionCard 카드를_저장한다(
            Long deviceId, LocalDate date, EmotionState state, int combination) {
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(deviceId).cardDate(date)
                .state(state).combinationIndex(combination).build());
        일별_스탬프를_저장한다(jdbc, deviceId, date, state, 3);
        개인_프레스를_저장한다(jdbc, deviceId, date, state, 125);
        return card;
    }

    private void 커밋_허용을_기다린다(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("커밋 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void 기기_테스트_데이터를_삭제한다(Long deviceId) {
        jdbc.sql("DELETE FROM collection_cards WHERE device_id = :id").param("id", deviceId).update();
        jdbc.sql("DELETE FROM device_daily_stamps WHERE device_id = :id").param("id", deviceId).update();
        jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :id").param("id", deviceId).update();
        devices.deleteById(deviceId);
    }
}
