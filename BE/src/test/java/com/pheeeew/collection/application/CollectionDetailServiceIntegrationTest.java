package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_빌더;
import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
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
class CollectionDetailServiceIntegrationTest {

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
    void 과거_카드의_고정_조합과_그_날짜의_집계를_조회한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate date = LocalDate.of(2026, 10, 21);
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId())
                .cardDate(date).combinationIndex(37).build());
        일별_스탬프를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 3);
        개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 125);
        개인_프레스를_저장한다(jdbc, owner.getId(), date.plusDays(1), EmotionState.ANGRY, 999);
        개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.EXHAUSTED, 888);

        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        일별_스탬프를_저장한다(jdbc, other.getId(), card.getCardDate(), EmotionState.ANGRY, 20);
        개인_프레스를_저장한다(jdbc, other.getId(), card.getCardDate(), EmotionState.ANGRY, 2000);

        // when
        var result = service.findDetail(owner.getPublicId(), card.getPublicId());

        // then
        assertThat(result.cardId()).isEqualTo(card.getPublicId());
        assertThat(result.date()).isEqualTo(date);
        assertThat(result.state()).isEqualTo(EmotionState.ANGRY);
        assertThat(result.stampCount()).isEqualTo(3);
        assertThat(result.pressCount()).isEqualTo(125);
        assertThat(result.images().characterUrl()).endsWith("/v1/characters/angry/03.png");
        assertThat(result.images().roundStampUrl()).endsWith("/v1/stamps-round/angry/06.png");
        assertThat(result.images().straightStampUrl()).endsWith("/v1/stamps-straight/angry/06.png");
    }

    @Test
    void 타인의_카드와_없는_카드는_동일한_오류로_거절한다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().requestId(UUID.randomUUID()).build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).build());

        // when / then
        for (UUID cardId : List.of(card.getPublicId(), UUID.randomUUID())) {
            assertThatThrownBy(() -> service.findDetail(other.getPublicId(), cardId))
                    .isInstanceOfSatisfying(CollectionException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(CollectionErrorCode.COLLECTION_CARD_NOT_FOUND));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void 발급된_카드의_집계가_하나라도_없으면_오류를_반환한다(boolean hasStamps, boolean hasPresses) {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var card = cards.saveAndFlush(기본_카드_빌더().deviceId(owner.getId()).build());
        if (hasStamps) {
            일별_스탬프를_저장한다(jdbc, owner.getId(), card.getCardDate(), EmotionState.ANGRY, 1);
        }
        if (hasPresses) {
            개인_프레스를_저장한다(jdbc, owner.getId(), card.getCardDate(), EmotionState.ANGRY, 100);
        }

        // when / then
        assertThatThrownBy(() -> service.findDetail(owner.getPublicId(), card.getPublicId()))
                .isInstanceOfSatisfying(CollectionException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("COLLECTION-006"));
        assertThat(cards.findById(card.getId())).isPresent();
    }

    @Test
    void 존재하지_않는_인증_기기는_상세를_조회할_수_없다() {
        // when / then
        assertThatThrownBy(() -> service.findDetail(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 전날_활동이나_카드가_늦게_커밋되면_다음_조회에_반영한다(boolean delayedCard) throws Exception {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        LocalDate date = LocalDate.of(2026, 10, 21);
        var card = 기본_카드_빌더().deviceId(owner.getId()).cardDate(date).combinationIndex(37)
                .claimedAt(Instant.parse("2026-10-21T14:59:59Z")).build();
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            일별_스탬프를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 1);
            개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 100);
            if (!delayedCard) {
                cards.saveAndFlush(card);
            }
            try (var executor = Executors.newFixedThreadPool(2)) {
                var pending = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    if (delayedCard) {
                        cards.saveAndFlush(card);
                    } else {
                        stamps.increase(date, EmotionState.ANGRY, owner.getId(), clock.instant());
                        개인_프레스를_저장한다(jdbc, owner.getId(), date, EmotionState.ANGRY, 5);
                    }
                    written.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("조회 대기 시간 초과");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return null;
                }));
                try {
                    assertThat(written.await(5, TimeUnit.SECONDS)).isTrue();
                    // when / then: 미커밋 활동이나 카드를 기다리지 않는다.
                    executor.submit(() -> {
                        if (delayedCard) {
                            assertThatThrownBy(() -> service.findDetail(owner.getPublicId(), card.getPublicId()))
                                    .isInstanceOfSatisfying(CollectionException.class,
                                            exception -> assertThat(exception.getErrorCode())
                                                    .isEqualTo(CollectionErrorCode.COLLECTION_CARD_NOT_FOUND));
                        } else {
                            var before = service.findDetail(owner.getPublicId(), card.getPublicId());
                            assertThat(before.stampCount()).isEqualTo(1);
                            assertThat(before.pressCount()).isEqualTo(100);
                        }
                    }).get(5, TimeUnit.SECONDS);
                } finally {
                    release.countDown();
                }
                pending.get(5, TimeUnit.SECONDS);
            }
            var after = service.findDetail(owner.getPublicId(), card.getPublicId());
            assertThat(after.cardId()).isEqualTo(card.getPublicId());
            assertThat(after.date()).isEqualTo(date);
            assertThat(after.stampCount()).isEqualTo(delayedCard ? 1 : 2);
            assertThat(after.pressCount()).isEqualTo(delayedCard ? 100 : 105);
            assertThat(after.images().characterUrl()).endsWith("/v1/characters/angry/03.png");
        } finally {
            release.countDown();
            jdbc.sql("DELETE FROM collection_cards WHERE device_id = :id").param("id", owner.getId()).update();
            jdbc.sql("DELETE FROM device_daily_stamps WHERE device_id = :id").param("id", owner.getId()).update();
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :id").param("id", owner.getId()).update();
            devices.deleteById(owner.getId());
        }
    }
}
