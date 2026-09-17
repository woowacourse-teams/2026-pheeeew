package com.pheeeew.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import com.pheeeew.activity.domain.repository.DeviceActivityAggregationStateRepository;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(DeviceActivityAggregationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceActivityAggregationServiceIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-17T03:00:00Z");

    @Autowired
    private DeviceActivityAggregationService service;
    @Autowired
    private DeviceActivityAggregationStateRepository stateRepository;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        stateRepository.deleteAllInBatch();
    }

    @Test
    void 처음_초기화하면_수집_시작_시각을_저장하고_집계_성공_상태는_비워_둔다() {
        // given / when
        service.initialize();

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
        service.initialize();
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
        service.initialize();

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
                    service.initialize();
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
}
