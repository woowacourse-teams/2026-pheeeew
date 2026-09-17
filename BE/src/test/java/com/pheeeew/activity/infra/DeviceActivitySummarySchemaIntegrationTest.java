package com.pheeeew.activity.infra;

import static com.pheeeew.activity.fixture.DeviceActivitySummaryFixture.기본_활동_집계_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.activity.domain.DeviceActivitySummary;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class DeviceActivitySummarySchemaIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @ParameterizedTest
    @EnumSource(DevicePlatform.class)
    void 기기_원본_없이_날짜와_플랫폼별_집계와_집계_시각을_보관한다(DevicePlatform platform) {
        // given
        DeviceActivitySummary summary = 기본_활동_집계_빌더().platform(platform).build();

        // when
        entityManager.persist(summary);
        entityManager.flush();
        entityManager.clear();
        DeviceActivitySummary saved = entityManager.find(DeviceActivitySummary.class, summary.getId());

        // then
        assertThat(saved.getActivityDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(saved.getPlatform()).isEqualTo(platform);
        assertThat(saved.getDau()).isEqualTo(3);
        assertThat(saved.getMau()).isEqualTo(10);
        assertThat(saved.getAggregatedAt()).isEqualTo(Instant.parse("2026-09-16T15:05:00Z"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_날짜와_플랫폼의_최종_집계는_중복_저장할_수_없다() {
        // given
        entityManager.persist(기본_활동_집계_빌더().build());
        entityManager.flush();
        DeviceActivitySummary duplicate = 기본_활동_집계_빌더().build();

        // when / then
        assertThatThrownBy(() -> entityManager.persist(duplicate))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_device_activity_summaries_date_platform");
    }

    @Test
    void 날짜나_플랫폼이_다르면_각각_보관하고_활동이_없었던_집계도_저장한다() {
        // given
        List<DeviceActivitySummary> summaries = List.of(
                기본_활동_집계_빌더().build(),
                기본_활동_집계_빌더().platform(DevicePlatform.IOS).build(),
                기본_활동_집계_빌더().activityDate(LocalDate.of(2026, 9, 17)).dau(0).mau(0).build()
        );

        // when
        summaries.forEach(entityManager::persist);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(summaries).extracting(DeviceActivitySummary::getId).doesNotHaveDuplicates();
        DeviceActivitySummary empty = entityManager.find(DeviceActivitySummary.class, summaries.getLast().getId());
        assertThat(empty.getDau()).isZero();
        assertThat(empty.getMau()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-1, 1", "0, -1", "2, 1"})
    void 음수_또는_DAU보다_작은_MAU는_객체와_DB에서_허용하지_않는다(long dau, long mau) {
        // given / when / then
        assertThatThrownBy(() -> 기본_활동_집계_빌더().dau(dau).mau(mau).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO device_activity_summaries
                    (activity_date, platform, dau, mau, aggregated_at, created_at, updated_at)
                VALUES (DATE '2026-09-16', 'ANDROID', :dau, :mau, NOW(), NOW(), NOW())
                """).param("dau", dau).param("mau", mau).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_device_activity_summaries_counts");
    }
}
