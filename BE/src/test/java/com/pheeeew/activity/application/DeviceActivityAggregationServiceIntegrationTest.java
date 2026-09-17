package com.pheeeew.activity.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pheeeew.activity.application.dto.DeviceActivityCount;
import com.pheeeew.activity.application.dto.DeviceActivitySnapshot;
import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import com.pheeeew.activity.domain.DeviceActivitySummary;
import com.pheeeew.activity.domain.repository.DeviceActivityAggregationStateRepository;
import com.pheeeew.activity.domain.repository.DeviceActivitySummaryRepository;
import com.pheeeew.activity.domain.repository.DeviceDailyActivityRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({DeviceActivityAggregationService.class, DeviceActivitySummaryService.class, DeviceDailyActivityService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceActivityAggregationServiceIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-17T03:00:00Z");

    @Autowired
    private DeviceActivityAggregationService service;
    @Autowired
    private DeviceActivityAggregationStateRepository stateRepository;
    @Autowired
    private DeviceActivitySummaryRepository summaryRepository;
    @Autowired
    private DeviceDailyActivityService activityService;
    @Autowired
    private DeviceDailyActivityRepository activityRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private Clock clock;

    private final List<Long> createdDeviceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        summaryRepository.deleteAllInBatch();
        stateRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
        deviceRepository.deleteAllByIdInBatch(createdDeviceIds);
    }

    @Test
    void 처음_초기화하면_수집_시작_시각을_저장하고_집계_성공_상태는_비워_둔다() {
        // given: 초기화가 늦어져도 전달받은 실제 수집 시작 시각을 사용한다.
        when(clock.instant()).thenReturn(STARTED_AT.plusSeconds(86_400));

        // when
        service.initialize(STARTED_AT);

        // then
        assertThat(stateRepository.findAll()).singleElement().satisfies(state -> {
            assertThat(state.getId()).isEqualTo(1L);
            assertThat(state.getCreatedAt()).isEqualTo(STARTED_AT);
            assertThat(state.getUpdatedAt()).isEqualTo(STARTED_AT);
            assertThat(state.getLastAggregatedAt()).isNull();
            assertThat(state.getLastFinalizedDate()).isNull();
        });
    }

    @Test
    void 다시_초기화해도_최초_시각과_기존_집계_진행_상태는_유지한다() {
        // given
        service.initialize(clock.instant());
        Instant aggregatedAt = STARTED_AT.plusSeconds(86_400);
        LocalDate finalizedDate = LocalDate.of(2026, 9, 17);
        jdbcClient.sql("""
                UPDATE device_activity_aggregation_states
                   SET last_aggregated_at = :aggregatedAt, last_finalized_date = :finalizedDate,
                       updated_at = :aggregatedAt
                 WHERE id = 1
                """).param("aggregatedAt", Timestamp.from(aggregatedAt))
                .param("finalizedDate", finalizedDate).update();
        when(clock.instant()).thenReturn(STARTED_AT.plusSeconds(172_800));

        // when
        service.initialize(clock.instant());

        // then
        DeviceActivityAggregationState state = stateRepository.findById(1L).orElseThrow();
        assertThat(state.getCreatedAt()).isEqualTo(STARTED_AT);
        assertThat(state.getUpdatedAt()).isEqualTo(aggregatedAt);
        assertThat(state.getLastAggregatedAt()).isEqualTo(aggregatedAt);
        assertThat(state.getLastFinalizedDate()).isEqualTo(finalizedDate);
    }

    @Test
    void 여러_서버처럼_동시에_초기화해도_한_행만_저장한다() throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(4);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> results = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                    service.initialize(clock.instant());
                    return null;
                }));
            }

            // when
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(stateRepository.findAll()).singleElement().satisfies(state -> {
            assertThat(state.getId()).isEqualTo(1L);
            assertThat(state.getCreatedAt()).isEqualTo(STARTED_AT);
        });
    }

    @Test
    void 수집_시작_상태가_없으면_처리_완료로_판단하지_않고_실패한다() {
        // given / when / then
        assertThatThrownBy(service::finalizeNextDate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활동 집계 상태가 초기화되지 않았습니다.");
        assertThat(summaryRepository.count()).isZero();
    }

    @Test
    void KST_날짜가_끝나면_두_플랫폼_집계와_완료_날짜를_함께_저장한다() {
        // given
        when(clock.instant()).thenReturn(Instant.parse("2026-09-16T15:00:00Z"));
        service.initialize(clock.instant());
        when(clock.instant()).thenReturn(Instant.parse("2026-09-17T14:59:59Z"));

        // when / then
        assertThat(service.finalizeNextDate()).isFalse();
        assertThat(summaryRepository.count()).isZero();
        when(clock.instant()).thenReturn(Instant.parse("2026-09-17T15:00:00Z"));
        assertThat(service.finalizeNextDate()).isTrue();
        assertThat(summaryRepository.findAll()).extracting(DeviceActivitySummary::getActivityDate)
                .containsExactly(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 17));
        DeviceActivityAggregationState state = stateRepository.findById(1L).orElseThrow();
        assertThat(state.getLastFinalizedDate()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(state.getLastAggregatedAt()).isNull();
        assertThat(service.finalizeNextDate()).isFalse();
    }

    @Test
    void 누락된_날짜는_수집_시작일부터_어제까지_하루씩_이어_처리한다() {
        // given
        service.initialize(clock.instant());
        when(clock.instant()).thenReturn(STARTED_AT.plusSeconds(3 * 86_400));

        // when
        for (int index = 0; index < 3; index++) {
            assertThat(service.finalizeNextDate()).isTrue();
            assertThat(stateRepository.findById(1L).orElseThrow().getLastFinalizedDate())
                    .isEqualTo(LocalDate.of(2026, 9, 17).plusDays(index));
        }

        // then
        assertThat(service.finalizeNextDate()).isFalse();
        assertThat(summaryRepository.findAll()).extracting(DeviceActivitySummary::getActivityDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 17),
                        LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 18),
                        LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 19));
    }

    @Test
    void 완료_날짜_갱신이_실패하면_두_플랫폼_집계도_롤백하고_재시도할_수_있다() {
        // given
        service.initialize(clock.instant());
        when(clock.instant()).thenReturn(STARTED_AT.plusSeconds(86_400));
        jdbcClient.sql("""
                ALTER TABLE device_activity_aggregation_states ADD CONSTRAINT ck_test_finalize_failure
                CHECK (last_finalized_date IS NULL)
                """).update();
        try {
            // when / then
            assertThatThrownBy(service::finalizeNextDate).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(summaryRepository.count()).isZero();
            assertThat(stateRepository.findById(1L).orElseThrow().getLastFinalizedDate()).isNull();
        } finally {
            jdbcClient.sql("ALTER TABLE device_activity_aggregation_states DROP CONSTRAINT ck_test_finalize_failure")
                    .update();
        }
        assertThat(service.finalizeNextDate()).isTrue();
        assertThat(summaryRepository.count()).isEqualTo(2);
    }

    @Test
    void 현재_집계를_반환하고_KST_자정부터_새_날짜의_DAU로_갱신한다() {
        // given
        service.initialize(clock.instant());
        Device device = deviceRepository.save(기본_기기_빌더().platform(DevicePlatform.ANDROID).build());
        createdDeviceIds.add(device.getId());
        activityService.save(device.getPublicId(), STARTED_AT);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-17T14:59:59Z"));

        // when
        DeviceActivitySnapshot beforeMidnight = service.update();

        // then
        assertThat(beforeMidnight.activityDate()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(beforeMidnight.aggregatedAt()).isEqualTo(Instant.parse("2026-09-17T14:59:59Z"));
        assertThat(beforeMidnight.collectionStartedAt()).isEqualTo(STARTED_AT);
        assertThat(beforeMidnight.counts()).containsExactly(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 1, 1),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0));
        assertThat(stateRepository.findById(1L).orElseThrow().getLastAggregatedAt())
                .isEqualTo(beforeMidnight.aggregatedAt());

        // when / then
        Instant midnight = Instant.parse("2026-09-17T15:00:00Z");
        when(clock.instant()).thenReturn(midnight);
        DeviceActivitySnapshot afterMidnight = service.update();
        assertThat(afterMidnight.activityDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(afterMidnight.aggregatedAt()).isEqualTo(midnight);
        assertThat(afterMidnight.counts()).containsExactly(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 1),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0));
        DeviceActivityAggregationState state = stateRepository.findById(1L).orElseThrow();
        assertThat(state.getLastAggregatedAt()).isEqualTo(midnight);
        assertThat(state.getLastFinalizedDate()).isNull();
        assertThat(summaryRepository.count()).isZero();
    }

    @Test
    void 활동이_없어도_정상_집계는_0과_성공_시각을_반환한다() {
        // given
        service.initialize(clock.instant());

        // when
        DeviceActivitySnapshot snapshot = service.update();

        // then
        assertThat(snapshot.counts()).containsExactly(
                DeviceActivityCount.of(DevicePlatform.ANDROID, 0, 0),
                DeviceActivityCount.of(DevicePlatform.IOS, 0, 0));
        assertThat(stateRepository.findById(1L).orElseThrow().getLastAggregatedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void 성공_시각_저장이_실패하면_결과를_반환하지_않고_이전_성공_시각을_유지한다() {
        // given
        service.initialize(clock.instant());
        service.update();
        Instant nextAttempt = STARTED_AT.plusSeconds(300);
        when(clock.instant()).thenReturn(nextAttempt);
        jdbcClient.sql("""
                ALTER TABLE device_activity_aggregation_states ADD CONSTRAINT ck_test_update_failure
                CHECK (last_aggregated_at = TIMESTAMPTZ '2026-09-17 03:00:00+00')
                """).update();
        try {
            // when / then
            assertThatThrownBy(service::update).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(stateRepository.findById(1L).orElseThrow().getLastAggregatedAt()).isEqualTo(STARTED_AT);
        } finally {
            jdbcClient.sql("ALTER TABLE device_activity_aggregation_states DROP CONSTRAINT ck_test_update_failure")
                    .update();
        }
        assertThat(service.update().aggregatedAt()).isEqualTo(nextAttempt);
        assertThat(stateRepository.findById(1L).orElseThrow().getLastAggregatedAt()).isEqualTo(nextAttempt);
    }

    @Test
    void 수집_시작_상태가_없으면_현재_집계도_실패한다() {
        // given / when / then
        assertThatThrownBy(service::update)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활동 집계 상태가 초기화되지 않았습니다.");
        assertThat(stateRepository.count()).isZero();
    }
}
