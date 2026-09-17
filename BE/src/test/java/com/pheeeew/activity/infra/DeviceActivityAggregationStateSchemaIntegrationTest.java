package com.pheeeew.activity.infra;

import static com.pheeeew.activity.fixture.DeviceActivityAggregationStateFixture.기본_집계_상태_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.activity.domain.DeviceActivityAggregationState;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class DeviceActivityAggregationStateSchemaIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void 수집_시작_시각을_보관하고_최초_집계_전의_성공_상태는_비워_둔다() {
        // given
        DeviceActivityAggregationState state = 기본_집계_상태_빌더().build();

        // when
        entityManager.persist(state);
        entityManager.flush();
        entityManager.clear();
        DeviceActivityAggregationState saved = entityManager.find(DeviceActivityAggregationState.class, 1L);

        // then
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getLastAggregatedAt()).isNull();
        assertThat(saved.getLastFinalizedDate()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 집계_상태를_갱신해도_최초_생성_시각을_유지하고_DB에서_복원한다() {
        // given
        entityManager.persist(기본_집계_상태_빌더().build());
        entityManager.flush();
        entityManager.clear();
        Instant startedAt = entityManager.find(DeviceActivityAggregationState.class, 1L).getCreatedAt();
        Instant aggregatedAt = startedAt.plusSeconds(86_400);
        LocalDate finalizedDate = startedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        jdbcClient.sql("""
                UPDATE device_activity_aggregation_states
                   SET last_aggregated_at = :aggregatedAt, last_finalized_date = :finalizedDate,
                       updated_at = :aggregatedAt
                 WHERE id = 1
                """).param("aggregatedAt", Timestamp.from(aggregatedAt))
                .param("finalizedDate", finalizedDate).update();

        // when
        entityManager.clear();
        DeviceActivityAggregationState saved = entityManager.find(DeviceActivityAggregationState.class, 1L);

        // then
        assertThat(saved.getCreatedAt()).isEqualTo(startedAt);
        assertThat(saved.getLastAggregatedAt()).isEqualTo(aggregatedAt);
        assertThat(saved.getLastFinalizedDate()).isEqualTo(finalizedDate);
    }

    @Test
    void 집계_상태를_다른_ID의_행으로_분리할_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO device_activity_aggregation_states
                    (id, created_at, updated_at)
                VALUES (2, NOW(), NOW())
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_device_activity_aggregation_states_singleton");
    }
}
