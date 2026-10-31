package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.domain.repository.CollectionDrawStateRepository;
import com.pheeeew.collection.domain.repository.DeviceDailyStampRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.collection.infra.CollectionAssetConfiguration;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        CollectionAssetConfiguration.class, CollectionAssetRepository.class, CollectionClaimService.class})
@TestPropertySource(properties = {"pheeeew.s3.bucket=collection-test-assets", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.s3.key-prefix=pheeeew/development/",
        "pheeeew.collection.card-version=v1"})
class CollectionQueryServiceIntegrationTest {

    @Autowired
    private CollectionQueryService service;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private CollectionClaimService claims;

    @Autowired
    private CollectionCardRepository cards;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CollectionDrawStateRepository drawStates;

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
    void 활동이_없어도_오늘의_다섯_감정과_부족분을_반환한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(result.stamps()).isEqualTo(CollectionTodayResult.Progress.of(0, 1));
        assertThat(result.presses()).isEqualTo(CollectionTodayResult.Progress.of(0, 100));
        assertThat(result.claimed()).isFalse();
        assertThat(result.claimable()).isFalse();
        assertThat(result.items()).extracting(item -> item.state()).containsExactly(EmotionState.values());
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.card()).isNull();
            assertThat(item.stamps().count()).isZero();
            assertThat(item.stamps().target()).isEqualTo(1);
            assertThat(item.stamps().remaining()).isEqualTo(1);
            assertThat(item.presses().count()).isZero();
            assertThat(item.presses().target()).isEqualTo(100);
            assertThat(item.presses().remaining()).isEqualTo(100);
        });
    }

    @Test
    void 조회_도중_KST_자정을_넘기면_새_날짜의_집계로_다시_조회한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 2);
        개인_프레스를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 22), EmotionState.ANGRY, 150);
        일별_스탬프를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), LocalDate.of(2026, 10, 23), EmotionState.ANGRY, 70);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59.999Z"),
                Instant.parse("2026-10-22T15:00:00Z"));

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(1);
                    assertThat(item.presses().count()).isEqualTo(70);
                    assertThat(item.presses().remaining()).isEqualTo(30);
                });
    }

    @ParameterizedTest
    @CsvSource({"0,70,1,30", "1,99,0,1", "1,100,0,0", "3,150,0,0", "2,0,0,100"})
    void 실제_누적_횟수를_유지하고_목표_초과분의_부족분은_영이다(
            long stamps, long presses, long remainingStamps, long remainingPresses) {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate date = LocalDate.of(2026, 10, 22);
        if (stamps > 0) {
            일별_스탬프를_저장한다(jdbc, device.getId(), date, EmotionState.FRUSTRATED, stamps);
        }
        if (presses > 0) {
            개인_프레스를_저장한다(jdbc, device.getId(), date, EmotionState.FRUSTRATED, presses);
        }

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.FRUSTRATED)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(stamps);
                    assertThat(item.stamps().target()).isEqualTo(1);
                    assertThat(item.stamps().remaining()).isEqualTo(remainingStamps);
                    assertThat(item.presses().count()).isEqualTo(presses);
                    assertThat(item.presses().target()).isEqualTo(100);
                    assertThat(item.presses().remaining()).isEqualTo(remainingPresses);
                });
    }

    @Test
    void 같은_날짜의_다른_기기와_다른_날짜와_감정의_집계를_섞지_않는다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 70);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.EXHAUSTED, 3);
        개인_프레스를_저장한다(jdbc, owner.getId(), today, EmotionState.EXHAUSTED, 20);
        일별_스탬프를_저장한다(jdbc, other.getId(), today, EmotionState.ANGRY, 7);
        개인_프레스를_저장한다(jdbc, other.getId(), today, EmotionState.ANGRY, 500);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today.minusDays(1), EmotionState.ANGRY, 9);
        개인_프레스를_저장한다(jdbc, owner.getId(), today.minusDays(1), EmotionState.ANGRY, 900);
        일별_스탬프를_저장한다(jdbc, owner.getId(), today.plusDays(1), EmotionState.ANGRY, 11);
        개인_프레스를_저장한다(jdbc, owner.getId(), today.plusDays(1), EmotionState.ANGRY, 1100);

        // when
        var ownResult = service.findToday(owner.getPublicId());
        var otherResult = service.findToday(other.getPublicId());

        // then
        assertThat(ownResult.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(1);
                    assertThat(item.presses().count()).isEqualTo(70);
                });
        assertThat(ownResult.items()).filteredOn(item -> item.state() == EmotionState.EXHAUSTED)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(3);
                    assertThat(item.presses().count()).isEqualTo(20);
                });
        assertThat(otherResult.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                .singleElement().satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(7);
                    assertThat(item.presses().count()).isEqualTo(500);
                });
    }

    @Test
    void 인증_기기가_존재하지_않으면_조회하지_못한다() {
        // given
        UUID missingDevice = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> service.findToday(missingDevice))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    void 날짜가_재조회_중에도_변하면_무한_반복하지_않고_재시도를_요청한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59Z"),
                Instant.parse("2026-10-22T15:00:00Z"), Instant.parse("2026-10-22T15:00:00Z"),
                Instant.parse("2026-10-23T15:00:00Z"));

        // when / then
        assertThatThrownBy(() -> service.findToday(device.getPublicId()))
                .isInstanceOfSatisfying(CollectionException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_DATE_CHANGED));
    }
    @Test
    void 목표를_달성해도_조회는_카드나_추첨_상태를_생성하지_않는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 100);

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.items()).allSatisfy(item -> assertThat(item.card()).isNull());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_cards WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_draw_states WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isZero();
    }

    @Test
    void 수령_후_횟수만_증가하고_조회한_카드와_그림은_유지된다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 100);
        var claimed = claims.claim(device.getPublicId(), today, EmotionState.ANGRY);
        var first = service.findToday(device.getPublicId());
        stamps.increase(today, EmotionState.ANGRY, device.getId(), clock.instant());
        stamps.increase(today, EmotionState.ANGRY, device.getId(), clock.instant());
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 50);

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(first.items()).filteredOn(item -> item.state() == EmotionState.ANGRY).singleElement()
                .satisfies(item -> assertThat(item.card()).isEqualTo(claimed));
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY).singleElement()
                .satisfies(item -> {
                    assertThat(item.stamps().count()).isEqualTo(3);
                    assertThat(item.presses().count()).isEqualTo(150);
                    assertThat(item.card()).isEqualTo(claimed);
                });
        assertThat(result.items()).filteredOn(item -> item.state() != EmotionState.ANGRY)
                .allSatisfy(item -> assertThat(item.card()).isNull());
        assertThat(result.stamps().count()).isEqualTo(3);
        assertThat(result.presses().count()).isEqualTo(150);
        assertThat(result.claimed()).isTrue();
        assertThat(result.claimable()).isFalse();
    }

    @Test
    void 다섯_감정을_합한_오늘_진행도로_수령_가능을_알린다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 60);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.EXHAUSTED, 40);

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.stamps()).isEqualTo(CollectionTodayResult.Progress.of(1, 1));
        assertThat(result.presses()).isEqualTo(CollectionTodayResult.Progress.of(100, 100));
        assertThat(result.claimed()).isFalse();
        assertThat(result.claimable()).isTrue();
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY).singleElement()
                .satisfies(item -> assertThat(item.presses().remaining()).isEqualTo(40));
    }

    @Test
    void 내_오늘_카드와_집계를_반환하고_다른_기기와_날짜의_카드는_제외한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        var own = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(today).combinationIndex(37).build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(other.getId()).cardDate(today).build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).cardDate(today.minusDays(1))
                .state(EmotionState.IRRITATED).build());

        일별_스탬프를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, owner.getId(), today, EmotionState.ANGRY, 100);

        // when
        var result = service.findToday(owner.getPublicId());

        // then
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY).singleElement()
                .satisfies(item -> {
                    assertThat(item.card().cardId()).isEqualTo(own.getPublicId());
                    assertThat(item.card().images().characterUrl())
                            .isEqualTo("https://collection-test-assets.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/characters/angry/03.png");
                    assertThat(item.card().images().roundStampUrl())
                            .isEqualTo("https://collection-test-assets.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-round/angry/06.png");
                    assertThat(item.card().images().straightStampUrl())
                            .isEqualTo("https://collection-test-assets.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-straight/angry/06.png");
                    assertThat(item.stamps().count()).isEqualTo(1);
                    assertThat(item.presses().count()).isEqualTo(100);
                });
        assertThat(result.items()).filteredOn(item -> item.state() != EmotionState.ANGRY)
                .allSatisfy(item -> assertThat(item.card()).isNull());
    }

    @Test
    void 자정을_넘겨_재조회하면_전날_카드도_제외한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).build());
        var cardDate = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, device.getId(), cardDate, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), cardDate, EmotionState.ANGRY, 100);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59.999Z"),
                Instant.parse("2026-10-22T15:00:00Z"));

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(result.items()).allSatisfy(item -> assertThat(item.card()).isNull());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 수령_잠금이_잡혀_있어도_오늘_조회는_커밋된_카드를_즉시_읽는다() throws Exception {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var card = cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).build());
            일별_스탬프를_저장한다(jdbc, device.getId(), card.getCardDate(), EmotionState.ANGRY, 1);
            개인_프레스를_저장한다(jdbc, device.getId(), card.getCardDate(), EmotionState.ANGRY, 100);
            drawStates.saveIfAbsent(device.getId(), EmotionState.ANGRY);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    jdbc.sql("SELECT id FROM devices WHERE id = :id FOR NO KEY UPDATE")
                            .param("id", device.getId()).query(Long.class).single();
                    locked.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("조회 완료를 기다리다 시간이 초과됐습니다.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return null;
                }));
                try {
                    assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

                    // when
                    var result = executor.submit(() -> service.findToday(device.getPublicId())).get(5, TimeUnit.SECONDS);

                    // then
                    assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY)
                            .singleElement().satisfies(item -> assertThat(item.card().cardId()).isEqualTo(card.getPublicId()));
                } finally {
                    release.countDown();
                }
                holder.get(5, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            jdbc.sql("DELETE FROM collection_cards WHERE device_id = :id").param("id", device.getId()).update();
            jdbc.sql("DELETE FROM collection_draw_states WHERE device_id = :id").param("id", device.getId()).update();
            jdbc.sql("DELETE FROM device_daily_stamps WHERE device_id = :id").param("id", device.getId()).update();
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :id").param("id", device.getId()).update();
            devices.deleteById(device.getId());
        }
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void 오늘_조회는_카드_감정의_집계가_없으면_그_횟수를_0으로_보여_준다(boolean hasStamps, boolean hasPresses) {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(device.getId()).build());
        if (hasStamps) {
            일별_스탬프를_저장한다(jdbc, device.getId(), card.getCardDate(), EmotionState.ANGRY, 1);
        }
        if (hasPresses) {
            개인_프레스를_저장한다(jdbc, device.getId(), card.getCardDate(), EmotionState.ANGRY, 100);
        }

        // when
        var result = service.findToday(device.getPublicId());

        // then
        assertThat(result.items()).filteredOn(item -> item.state() == EmotionState.ANGRY).singleElement()
                .satisfies(item -> {
                    assertThat(item.card().cardId()).isEqualTo(card.getPublicId());
                    assertThat(item.stamps().count()).isEqualTo(hasStamps ? 1 : 0);
                    assertThat(item.presses().count()).isEqualTo(hasPresses ? 100 : 0);
                });
        assertThat(result.claimed()).isTrue();
    }

    @Test
    void 활동이_없는_감정으로_받은_카드도_오늘_조회_상세_보관함에서_0으로_조회한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate today = LocalDate.of(2026, 10, 22);
        일별_스탬프를_저장한다(jdbc, device.getId(), today, EmotionState.DISCOURAGED, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.ANGRY, 60);
        개인_프레스를_저장한다(jdbc, device.getId(), today, EmotionState.EXHAUSTED, 40);
        var claimed = claims.claim(device.getPublicId(), today, EmotionState.FRUSTRATED);

        // when
        var todayResult = service.findToday(device.getPublicId());
        var detail = service.findDetail(device.getPublicId(), claimed.cardId());
        var history = service.findHistory(device.getPublicId(), null, 20, null);

        // then
        assertThat(todayResult.items()).filteredOn(item -> item.state() == EmotionState.FRUSTRATED).singleElement()
                .satisfies(item -> {
                    assertThat(item.card()).isEqualTo(claimed);
                    assertThat(item.stamps().count()).isZero();
                    assertThat(item.presses().count()).isZero();
                });
        assertThat(todayResult.claimed()).isTrue();
        assertThat(detail.state()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(detail.stampCount()).isZero();
        assertThat(detail.pressCount()).isZero();
        assertThat(history.items()).singleElement().satisfies(item -> {
            assertThat(item.cardId()).isEqualTo(claimed.cardId());
            assertThat(item.stampCount()).isZero();
            assertThat(item.pressCount()).isZero();
        });
    }
}
