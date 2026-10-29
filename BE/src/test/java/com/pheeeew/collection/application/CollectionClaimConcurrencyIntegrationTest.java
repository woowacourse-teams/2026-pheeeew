package com.pheeeew.collection.application;

import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static com.pheeeew.collection.fixture.CollectionCardFixture.기본_카드_이미지;

import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.domain.repository.CollectionDrawStateRepository;
import com.pheeeew.collection.exception.CollectionErrorCode;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({CollectionClaimService.class, CollectionProgressRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionClaimConcurrencyIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Autowired
    private CollectionClaimService service;

    @Autowired
    private CollectionDrawStateRepository states;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private CollectionAssetService versions;

    private final List<Long> deviceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T03:00:00Z"));
        when(versions.activeVersion()).thenReturn("v1");
        when(versions.findImages(anyString(), any(), anyInt())).thenReturn(기본_카드_이미지());
    }

    @AfterEach
    void cleanUp() {
        for (Long deviceId : deviceIds) {
            jdbc.sql("DELETE FROM collection_cards WHERE device_id = :id").param("id", deviceId).update();
            jdbc.sql("DELETE FROM collection_draw_states WHERE device_id = :id").param("id", deviceId).update();
            jdbc.sql("DELETE FROM device_daily_stamps WHERE device_id = :id").param("id", deviceId).update();
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :id").param("id", deviceId).update();
            devices.deleteById(deviceId);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 활동_순서와_관계없이_먼저_저장한_활동만으로는_거절하고_나머지_활동_커밋_후_수령한다(boolean pressFirst) {
        // given
        var transaction = new TransactionTemplate(transactionManager);
        var device = transaction.execute(status -> {
            var created = devices.saveAndFlush(기본_기기_빌더().build());
            deviceIds.add(created.getId());
            if (pressFirst) {
                개인_프레스를_저장한다(jdbc, created.getId(), TODAY, EmotionState.ANGRY, 100);
            } else {
                일별_스탬프를_저장한다(jdbc, created.getId(), TODAY, EmotionState.ANGRY, 1);
            }
            return created;
        });

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOfSatisfying(CollectionException.class,
                        failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET));
        assertThat(cardCount(device.getId())).isZero();
        transaction.executeWithoutResult(status -> {
            if (pressFirst) {
                일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
            } else {
                개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
            }
        });
        var claimed = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        assertThat(claimed.cardId()).isNotNull();
        assertThat(cardCount(device.getId())).isEqualTo(1);
        assertThat(service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY)).isEqualTo(claimed);
    }

    @Test
    void 최초_수령이_동시에_들어와도_추첨_상태와_카드를_한_번만_만든다() throws Exception {
        // given
        var device = createEligibleDevice();
        var start = new CyclicBarrier(6);
        List<Future<CollectionCardResult>> requests = new ArrayList<>();

        // when
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (int count = 0; count < 6; count++) {
                requests.add(executor.submit(() -> {
                    start.await(15, TimeUnit.SECONDS);
                    return service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
                }));
            }
            var first = requests.getFirst().get(20, TimeUnit.SECONDS);
            for (var request : requests) {
                assertThat(request.get(20, TimeUnit.SECONDS)).isEqualTo(first);
            }
        }

        // then
        assertThat(cardCount(device.getId())).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_draw_states WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void 서로_다른_감정으로_동시에_수령해도_카드는_한_장이고_나머지는_409로_거절한다() throws Exception {
        // given
        var device = createEligibleDevice();
        var start = new CyclicBarrier(EmotionState.values().length);
        Map<EmotionState, Future<CollectionCardResult>> requests = new EnumMap<>(EmotionState.class);

        // when
        try (var executor = Executors.newFixedThreadPool(EmotionState.values().length)) {
            for (var state : EmotionState.values()) {
                requests.put(state, executor.submit(() -> {
                    start.await(15, TimeUnit.SECONDS);
                    return service.claim(device.getPublicId(), TODAY, state);
                }));
            }
            List<CollectionCardResult> claimed = new ArrayList<>();
            List<Throwable> rejected = new ArrayList<>();
            for (var request : requests.values()) {
                try {
                    claimed.add(request.get(20, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    rejected.add(exception.getCause());
                }
            }

            // then
            assertThat(claimed).hasSize(1);
            assertThat(rejected).hasSize(EmotionState.values().length - 1)
                    .allSatisfy(failure -> assertThat(failure).isInstanceOfSatisfying(CollectionException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(CollectionErrorCode.COLLECTION_ALREADY_CLAIMED_TODAY)));
            assertThat(cardCount(device.getId())).isEqualTo(1);
            assertThat(jdbc.sql("SELECT state FROM collection_draw_states WHERE device_id = :id")
                    .param("id", device.getId()).query(String.class).list())
                    .containsExactly(claimed.getFirst().state().name());
        }
    }

    @Test
    void 카드_삽입_뒤_회차_저장이_실패하면_카드와_추첨만_롤백하고_활동은_유지한다() {
        // given
        var device = createEligibleDevice();
        states.saveIfAbsent(device.getId(), EmotionState.ANGRY);
        var transaction = new TransactionTemplate(transactionManager);

        // when
        assertThatThrownBy(() -> transaction.execute(status -> {
            jdbc.sql("""
                    ALTER TABLE collection_draw_states ADD CONSTRAINT ck_test_claim_rollback
                    CHECK (last_combination_index IS NULL) NOT VALID
                    """).update();
            return service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        })).isInstanceOf(DataIntegrityViolationException.class);

        // then
        assertThat(cardCount(device.getId())).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_draw_states WHERE device_id = :id AND last_combination_index IS NOT NULL")
                .param("id", device.getId()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT stamp_count FROM device_daily_stamps WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT press_count FROM device_daily_presses WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isEqualTo(100);
        assertThat(service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY).cardId()).isNotNull();
    }

    @Test
    void 자정까지_발급_잠금을_기다린_전날_미수령_요청은_거절한다() throws Exception {
        // given
        var device = createEligibleDevice();
        states.saveIfAbsent(device.getId(), EmotionState.ANGRY);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-22T14:59:59.999Z"));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> transaction.execute(status -> {
                jdbc.sql("SELECT id FROM collection_draw_states WHERE device_id = :id FOR UPDATE")
                        .param("id", device.getId()).query(Long.class).single();
                locked.countDown();
                await(release);
                return null;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var request = executor.submit(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY));
                awaitBlockedClaim();

                // when
                when(clock.instant()).thenReturn(Instant.parse("2026-10-22T15:00:00Z"));
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);

                // then
                assertThatThrownBy(() -> request.get(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class,
                        failure -> assertThat(failure.getCause()).isInstanceOfSatisfying(CollectionException.class,
                                cause -> assertThat(cause.getErrorCode()).isEqualTo(CollectionErrorCode.COLLECTION_CLAIM_DATE_INVALID)));
                assertThat(cardCount(device.getId())).isZero();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void 자정_전에_최종_검사를_통과한_수령은_자정_뒤_커밋되어도_유효하다() {
        // given
        var device = createEligibleDevice();
        Instant verifiedAt = Instant.parse("2026-10-22T14:59:59.999Z");
        when(clock.instant()).thenReturn(verifiedAt);
        var transaction = new TransactionTemplate(transactionManager);

        // when
        var card = transaction.execute(status -> {
            var result = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
            when(clock.instant()).thenReturn(Instant.parse("2026-10-22T15:00:00Z"));
            return result;
        });

        // then
        assertThat(card.date()).isEqualTo(TODAY);
        assertThat(card.claimedAt()).isEqualTo(verifiedAt);
        assertThat(service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY)).isEqualTo(card);
    }

    @Test
    void 커밋되지_않은_활동은_수령에_쓰지_않고_커밋_후_재시도할_수_있다() throws Exception {
        // given
        var device = createEligibleDevice();
        jdbc.sql("UPDATE device_daily_presses SET press_count = 99 WHERE device_id = :id")
                .param("id", device.getId()).update();
        var updated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var activity = executor.submit(() -> transaction.execute(status -> {
                개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
                updated.countDown();
                await(release);
                return null;
            }));
            try {
                assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();

                // when / then
                assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                        .isInstanceOfSatisfying(CollectionException.class,
                                failure -> assertThat(failure.getErrorCode())
                                        .isEqualTo(CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET));
                assertThat(cardCount(device.getId())).isZero();
                release.countDown();
                activity.get(10, TimeUnit.SECONDS);
                assertThat(service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY).cardId()).isNotNull();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void 이미지_URL을_만들지_못하면_신규_발급은_롤백하고_기존_카드는_보존한다() {
        // given
        var device = createEligibleDevice();
        when(versions.findImages(anyString(), any(), anyInt()))
                .thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));

        // when / then
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOf(CollectionException.class);
        assertThat(cardCount(device.getId())).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM collection_draw_states WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isZero();

        doReturn(기본_카드_이미지()).when(versions).findImages(anyString(), any(), anyInt());
        assertThat(jdbc.sql("SELECT stamp_count FROM device_daily_stamps WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT press_count FROM device_daily_presses WHERE device_id = :id")
                .param("id", device.getId()).query(Long.class).single()).isEqualTo(100);
        var first = service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY);
        assertThat(first.images()).isEqualTo(기본_카드_이미지());
        when(versions.findImages(anyString(), any(), anyInt()))
                .thenThrow(new CollectionException(CollectionErrorCode.COLLECTION_ASSETS_NOT_READY));
        assertThatThrownBy(() -> service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY))
                .isInstanceOf(CollectionException.class);
        assertThat(cardCount(device.getId())).isEqualTo(1);

        doReturn(기본_카드_이미지()).when(versions).findImages(anyString(), any(), anyInt());
        assertThat(service.claim(device.getPublicId(), TODAY, EmotionState.ANGRY)).isEqualTo(first);
    }

    private Device createEligibleDevice() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            var device = devices.saveAndFlush(기본_기기_빌더().build());
            deviceIds.add(device.getId());
            일별_스탬프를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 1);
            개인_프레스를_저장한다(jdbc, device.getId(), TODAY, EmotionState.ANGRY, 100);
            return device;
        });
    }

    private long cardCount(Long deviceId) {
        return jdbc.sql("SELECT COUNT(*) FROM collection_cards WHERE device_id = :id")
                .param("id", deviceId).query(Long.class).single();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제를 기다리는 시간이 초과했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void awaitBlockedClaim() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean blocked = jdbc.sql("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                    WHERE datname = current_database() AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock' AND query LIKE '%collection_draw_states%')
                    """).query(Boolean.class).single();
            if (blocked) {
                return;
            }
        }
        throw new AssertionError("수령 요청이 DB 잠금을 기다리는 상태를 확인하지 못했습니다.");
    }
}
