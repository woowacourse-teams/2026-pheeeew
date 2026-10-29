package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_이미지;

import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionDrawStateRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({CollectionClaimService.class, CollectionProgressRepository.class})
class CollectionClaimServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Autowired
    private CollectionClaimService service;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CollectionCardRepository cards;

    @Autowired
    private CollectionDrawStateRepository drawStates;

    @Autowired
    private EntityManager entities;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private CollectionAssetService versions;

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
        when(versions.activeVersion()).thenReturn("v1");
        when(versions.findImages(anyString(), any(), anyInt())).thenReturn(기본_카드_이미지());
    }

    @Test
    void 조건을_채운_기기가_당일_카드를_수령하고_재요청하면_같은_카드를_받는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);

        // when
        var first = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        var repeated = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);

        // then
        assertThat(repeated).isEqualTo(first);
        assertThat(first.cardId()).isNotNull();
        assertThat(first.date()).isEqualTo(TODAY);
        assertThat(first.state()).isEqualTo(EmotionState.ANGRY);
        assertThat(first.cycle()).isEqualTo(1);
        assertThat(first.combinationIndex()).isBetween(0, 255);
        assertThat(first.cardVersion()).isEqualTo("v1");
        assertThat(first.claimedAt()).isEqualTo(Instant.parse("2026-10-22T03:00:00Z"));
    }

    @Test
    void 수령한_카드와_추첨_상태의_회차_조합_버전_시각을_저장하고_다시_읽는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        Long deviceId = device.getId();
        일별_스탬프를_저장한다(jdbc, deviceId, TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, deviceId, TODAY, EmotionState.ANGRY, 100);

        // when
        var result = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        entities.flush();
        entities.clear();
        var storedCard = cards.findByDeviceIdAndCardDate(deviceId, TODAY).orElseThrow();
        var storedState = drawStates.findByDeviceIdAndState(deviceId, EmotionState.ANGRY).orElseThrow();

        // then
        assertThat(storedCard.getPublicId()).isEqualTo(result.cardId());
        assertThat(storedCard.getDeviceId()).isEqualTo(deviceId);
        assertThat(storedCard.getCardDate()).isEqualTo(TODAY);
        assertThat(storedCard.getState()).isEqualTo(EmotionState.ANGRY);
        assertThat(storedCard.getCycle()).isEqualTo(result.cycle());
        assertThat(storedCard.getCombinationIndex()).isEqualTo(result.combinationIndex());
        assertThat(storedCard.getCardVersion()).isEqualTo("v1");
        assertThat(storedCard.getClaimedAt()).isEqualTo(result.claimedAt());
        assertThat(storedCard.getCreatedAt()).isNotNull();
        assertThat(storedCard.getUpdatedAt()).isNotNull();
        assertThat(storedState.getDeviceId()).isEqualTo(deviceId);
        assertThat(storedState.getState()).isEqualTo(EmotionState.ANGRY);
        assertThat(storedState.getCycle()).isEqualTo(result.cycle());
        assertThat(storedState.getLastCombinationIndex()).isEqualTo(result.combinationIndex());
        assertThat(storedState.getCreatedAt()).isNotNull();
        assertThat(storedState.getUpdatedAt()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"0,100", "1,99", "0,0", "1,0"})
    void 스탬프와_프레스_조건을_모두_채우지_않으면_발급하지_않는다(long stamps, long presses) {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        if (stamps > 0) {
            일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, stamps);
        }
        if (presses > 0) {
            개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, presses);
        }

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET));
        assertThat(cards.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void 조건을_채워도_지난_날짜와_미래_날짜의_미수령_카드는_발급하지_않는다(int offset) {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        var date = TODAY.plusDays(offset);
        일별_스탬프를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 100);

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), date, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_CLAIM_DATE_INVALID));
        assertThat(cards.count()).isZero();
    }

    @Test
    void 이미_수령한_카드는_다음_날_신규_발급이_차단돼도_저장된_버전으로_반환한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
        var first = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T15:00:00Z"));
        when(versions.activeVersion()).thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));

        // when
        var result = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);

        // then
        assertThat(result).isEqualTo(first);
        assertThat(cards.count()).isEqualTo(1);
    }

    @Test
    void 집계_확인_사이에_자정이_지나면_전날_카드를_발급하지_않는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59.999Z"),
                Instant.parse("2026-10-22T15:00:00Z"));

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_CLAIM_DATE_INVALID));
        assertThat(cards.count()).isZero();
    }

    @Test
    void 다른_기기와_다른_날짜의_활동은_합산하지_않는다() {
        // given
        var owner = devices.saveAndFlush(기본_기기_빌더().build());
        var other = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, owner.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, other.getId(), TODAY, EmotionState.ANGRY, 100);
        개인_프레스를_저장한다(jdbc, owner.getId(), TODAY.minusDays(1), EmotionState.ANGRY, 100);

        // when / then
        assertThatThrownBy(() -> service.claim(owner.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET));
        assertThat(cards.count()).isZero();
    }

    @Test
    void 자산이_준비되지_않으면_조합을_소비하거나_카드를_저장하지_않는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
        when(versions.activeVersion()).thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));
        assertThat(cards.count()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_draw_states WHERE device_id = :deviceId AND last_combination_index IS NOT NULL")
                .param("deviceId", device.getId()).query(Long.class).single()).isZero();
    }

    @Test
    void 저장된_회차의_미사용_조합을_수령하고_다음_회차는_직전_조합을_제외한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        jdbc.sql("""
                INSERT INTO collection_draw_states
                    (device_id, state, cycle, last_combination_index, created_at, updated_at)
                VALUES (:deviceId, 'ANGRY', 1, 254, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("deviceId", device.getId()).update();
        jdbc.sql("""
                INSERT INTO collection_cards
                    (public_id, device_id, card_date, state, cycle, combination_index, card_version,
                     claimed_at, created_at, updated_at)
                SELECT gen_random_uuid(), :deviceId, CAST(:today AS date) - (n + 1), 'ANGRY', 1, n, 'v1',
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM generate_series(0, 254) AS n
                """).param("deviceId", device.getId()).param("today", TODAY).update();
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);

        // when
        var last = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-23T03:00:00Z"));
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY.plusDays(1), EmotionState.ANGRY, 100);
        var next = service.claim(device.getPublicId(), TODAY.plusDays(1), EmotionState.ANGRY);

        // then
        assertThat(last.cycle()).isEqualTo(1);
        assertThat(last.combinationIndex()).isEqualTo(255);
        assertThat(next.cycle()).isEqualTo(2);
        assertThat(next.combinationIndex()).isBetween(0, 254);
        assertThat(cards.findUsedCombinations(device.getId(), EmotionState.ANGRY, 1)).hasSize(256);
        assertThat(cards.findUsedCombinations(device.getId(), EmotionState.ANGRY, 2)).containsExactly(next.combinationIndex());
    }

    @Test
    void 기기와_감정마다_독립적으로_첫_회차의_카드를_수령한다() {
        // given
        var first = devices.saveAndFlush(기본_기기_빌더().build());
        var second = devices.saveAndFlush(기본_기기_빌더().build());
        for (var device : java.util.List.of(first, second)) {
            for (var date : java.util.List.of(TODAY, TODAY.plusDays(1))) {
                일별_스탬프를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 1);
                개인_프레스를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 100);
            }
        }

        // when
        var firstAngry = service.claim(first.getPublicId(), TODAY, EmotionState.ANGRY);
        var secondAngry = service.claim(second.getPublicId(), TODAY, EmotionState.ANGRY);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-23T03:00:00Z"));
        var firstExhausted = service.claim(first.getPublicId(), TODAY.plusDays(1), EmotionState.EXHAUSTED);
        var secondExhausted = service.claim(second.getPublicId(), TODAY.plusDays(1), EmotionState.EXHAUSTED);

        // then
        assertThat(java.util.List.of(firstAngry, firstExhausted, secondAngry, secondExhausted))
                .extracting(card -> card.cardId()).doesNotHaveDuplicates();
        assertThat(java.util.List.of(firstAngry, firstExhausted, secondAngry, secondExhausted))
                .allSatisfy(card -> assertThat(card.cycle()).isEqualTo(1));
        assertThat(cards.count()).isEqualTo(4);
    }

    @Test
    void 다섯_감정의_활동을_합산해_조건을_채우면_활동이_없는_감정으로도_수령한다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.DISCOURAGED, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 60);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.EXHAUSTED, 40);

        // when
        var card = service.claim(device.getPublicId(), TODAY, EmotionState.FRUSTRATED);

        // then
        assertThat(card.state()).isEqualTo(EmotionState.FRUSTRATED);
        assertThat(cards.count()).isOne();
    }

    @Test
    void 합산_프레스가_100회_미만이면_발급하지_않는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 50);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.EXHAUSTED, 49);

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET));
        assertThat(cards.count()).isZero();
    }

    @Test
    void 오늘_카드를_받은_뒤_다른_감정으로_요청하면_409로_거절하고_조합을_소비하지_않는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
        var claimed = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);

        // when
        Throwable rejected = catchThrowable(() -> service.claim(device.getPublicId(), TODAY, EmotionState.EXHAUSTED));
        var repeated = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);

        // then
        assertThat(rejected).isInstanceOfSatisfying(CollectionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_ALREADY_CLAIMED_TODAY));
        assertThat(repeated).isEqualTo(claimed);
        assertThat(cards.count()).isOne();
        assertThat(drawStates.findByDeviceIdAndState(device.getId(), EmotionState.EXHAUSTED)).isEmpty();
    }

    @Test
    void 다음_날에는_조건을_다시_채우면_다른_감정으로_한_장을_더_받는다() {
        // given
        var device = devices.saveAndFlush(기본_기기_빌더().build());
        for (var date : java.util.List.of(TODAY, TODAY.plusDays(1))) {
            일별_스탬프를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 1);
            개인_프레스를_저장한다(jdbc, device.getId(), date, EmotionState.ANGRY, 100);
        }
        var yesterdayCard = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-23T03:00:00Z"));

        // when
        var todayCard = service.claim(device.getPublicId(), TODAY.plusDays(1), EmotionState.EXHAUSTED);
        var repeatedYesterday = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        Throwable otherYesterday = catchThrowable(() -> service.claim(device.getPublicId(), TODAY, EmotionState.EXHAUSTED));

        // then
        assertThat(todayCard.state()).isEqualTo(EmotionState.EXHAUSTED);
        assertThat(todayCard.cardId()).isNotEqualTo(yesterdayCard.cardId());
        assertThat(repeatedYesterday).isEqualTo(yesterdayCard);
        assertThat(otherYesterday).isInstanceOfSatisfying(CollectionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_CLAIM_DATE_INVALID));
        assertThat(cards.count()).isEqualTo(2);
    }

    @Test
    void 존재하지_않는_인증_기기는_카드를_수령하지_못한다() {
        // given / when / then
        assertThatThrownBy(() -> service.claim(UUID.randomUUID(), TODAY, EmotionState.ANGRY))
                .isInstanceOf(DeviceException.class);
        assertThat(cards.count()).isZero();
    }
}
