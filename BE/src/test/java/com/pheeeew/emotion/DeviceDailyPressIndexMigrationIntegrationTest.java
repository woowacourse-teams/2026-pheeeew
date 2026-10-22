package com.pheeeew.emotion;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DeviceDailyPressIndexMigrationIntegrationTest {

    @Test
    void 인덱스_순서_변경은_기존_집계와_유일성_및_UPSERT를_보존한다() {
        // given
        var container = postgis();
        String database = "press_index_" + UUID.randomUUID().toString().replace("-", "");
        var admin = JdbcClient.create(new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        var dataSource = new DriverManagerDataSource(
                container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database),
                container.getUsername(), container.getPassword());
        admin.sql("CREATE DATABASE " + database).update();
        try {
            Flyway.configure().dataSource(dataSource).target("20261022.1").load().migrate();
            JdbcClient jdbc = JdbcClient.create(dataSource);
            jdbc.sql("""
                    INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
                    SELECT gen_random_uuid(), gen_random_uuid(), 'ANDROID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM generate_series(1, 2)
                    """).update();
            jdbc.sql("""
                    INSERT INTO device_daily_presses
                        (press_date, state, device_id, press_count, created_at, updated_at)
                    SELECT day, state, devices.id, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM devices
                    CROSS JOIN (VALUES (DATE '2026-10-21'), (DATE '2026-10-22')) days(day)
                    CROSS JOIN (VALUES ('ANGRY'), ('EXHAUSTED')) states(state)
                    """).update();
            var before = jdbc.sql("SELECT to_jsonb(p)::text FROM device_daily_presses p ORDER BY id")
                    .query(String.class).list();

            // when
            Flyway migration = Flyway.configure().dataSource(dataSource).target("20261022.2").load();
            migration.migrate();

            // then
            assertThat(jdbc.sql("SELECT to_jsonb(p)::text FROM device_daily_presses p ORDER BY id")
                    .query(String.class).list()).isEqualTo(before);
            assertThat(jdbc.sql("""
                    SELECT pg_get_constraintdef(oid)
                    FROM pg_constraint
                    WHERE conrelid = 'device_daily_presses'::regclass
                      AND conname = 'uk_device_daily_presses_date_state_device'
                    """).query(String.class).single()).isEqualTo("UNIQUE (press_date, device_id, state)");
            assertThatThrownBy(() -> jdbc.sql("""
                    INSERT INTO device_daily_presses
                        (press_date, state, device_id, press_count, created_at, updated_at)
                    SELECT press_date, state, device_id, press_count, created_at, updated_at
                    FROM device_daily_presses LIMIT 1
                    """).update()).isInstanceOf(DataIntegrityViolationException.class);

            Long deviceId = jdbc.sql("SELECT MIN(id) FROM devices").query(Long.class).single();
            var repository = new DeviceDailyPressRepository(jdbc);
            repository.increase(LocalDate.of(2026, 10, 22), "ANGRY", deviceId, 3, Instant.parse("2026-10-22T03:00:00Z"));
            assertThat(jdbc.sql("SELECT press_count FROM device_daily_presses ORDER BY press_count")
                    .query(Long.class).list()).containsExactly(100L, 100L, 100L, 100L, 100L, 100L, 100L, 103L);
            repository.increase(LocalDate.of(2026, 10, 23), "ANGRY", deviceId, 2, Instant.parse("2026-10-23T03:00:00Z"));
            assertThat(repository.findByPressDateAndDeviceId(LocalDate.of(2026, 10, 23), deviceId))
                    .singleElement().satisfies(sum -> assertThat(sum.pressCount()).isEqualTo(2));
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.sql("SELECT COUNT(*) FROM device_daily_presses").query(Long.class).single()).isEqualTo(9);
        } finally {
            admin.sql("DROP DATABASE " + database).update();
        }
    }
}
