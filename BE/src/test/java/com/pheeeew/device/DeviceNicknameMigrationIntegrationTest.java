package com.pheeeew.device;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.support.PostgisDataJpaTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
class DeviceNicknameMigrationIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 닉네임_컬럼을_추가해도_기기와_인증_정보를_보존한다() {
        // given: 이전 스키마를 가진 격리 DB에 기존 기기와 인증 정보를 준비한다.
        String database = "nickname_migration_" + UUID.randomUUID().toString().replace("-", "");
        var container = postgis();
        var dataSource = new DriverManagerDataSource(
                container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database),
                container.getUsername(), container.getPassword());
        jdbc.sql("CREATE DATABASE " + database).update();
        try {
            Flyway.configure().dataSource(dataSource).target("20261006.1").load().migrate();
            JdbcClient isolated = JdbcClient.create(dataSource);
            isolated.sql("""
                    INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
                    SELECT gen_random_uuid(), gen_random_uuid(), 'ANDROID',
                           CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 hour'
                    FROM generate_series(1, 100)
                    """).update();
            isolated.sql("""
                    INSERT INTO device_refresh_tokens (device_id, session_id, token_hash, created_at, updated_at)
                    SELECT id, gen_random_uuid(), repeat('a', 64), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM devices ORDER BY id LIMIT 1
                    """).update();
            List<Map<String, Object>> devices = 기기_기록(isolated);
            Map<String, Object> token = isolated.sql("SELECT * FROM device_refresh_tokens").query().singleRow();

            // when
            Flyway latest = Flyway.configure().dataSource(dataSource).target("20261007.1").load();
            latest.migrate();

            // then: 한국어 닉네임 백필 전에는 기존 기록과 인증 참조만 보존한다.
            assertThat(기기_기록(isolated)).isEqualTo(devices);
            assertThat(isolated.sql("SELECT * FROM device_refresh_tokens").query().singleRow()).isEqualTo(token);
            assertThat(isolated.sql("SELECT count(*) FROM devices WHERE nickname IS NULL")
                    .query(Long.class).single()).isEqualTo(100);
            assertThat(isolated.sql("""
                    SELECT column_default IS NULL AND is_nullable = 'YES' AND character_maximum_length = 10
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'nickname'
                    """).query(Boolean.class).single()).isTrue();

            // 서버 재시작 시 migration을 다시 확인해도 저장한 닉네임을 덮어쓰지 않는다.
            isolated.sql("UPDATE devices SET nickname = '스타크' WHERE id = (SELECT min(id) FROM devices)")
                    .update();
            latest.migrate();
            assertThat(isolated.sql("SELECT nickname FROM devices WHERE nickname IS NOT NULL")
                    .query(String.class).single()).isEqualTo("스타크");
            assertThat(기기_기록(isolated)).isEqualTo(devices);
            기기를_저장한다(isolated);
            assertThat(isolated.sql("SELECT count(*) FROM devices").query(Long.class).single()).isEqualTo(101);
        } finally {
            jdbc.sql("DROP DATABASE " + database).update();
        }
    }

    @Test
    void 백필_전에는_닉네임을_생략하는_구버전_기기_등록을_허용한다() {
        // given / when
        기기를_저장한다(jdbc);
        기기를_저장한다(jdbc);

        // then
        assertThat(jdbc.sql("SELECT count(*) FROM devices WHERE nickname IS NULL")
                .query(Long.class).single()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "Star K", "가나다라마바사아자차", "ㄱㅏ"})
    void 한글_영문_중간_공백과_길이_경계를_허용한다(String nickname) {
        // given
        기기를_저장한다(jdbc);

        // when
        jdbc.sql("UPDATE devices SET nickname = :nickname").param("nickname", nickname).update();

        // then
        assertThat(jdbc.sql("SELECT nickname FROM devices").query(String.class).single()).isEqualTo(nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "익명", " 스타크", "스타크 ", "스타1", "스타!",
            "Star\tK", "Star\nK", "スター", "😀", "abcdefghijk"
    })
    void 닉네임_규칙을_위반한_값은_DB에서도_거부한다(String nickname) {
        // given
        기기를_저장한다(jdbc);

        // when / then
        assertThatThrownBy(() -> jdbc.sql("UPDATE devices SET nickname = :nickname")
                .param("nickname", nickname).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Stark", "stark"})
    void 동일하거나_영문_대소문자만_다른_중복_닉네임을_거부한다(String nickname) {
        // given
        기기를_저장한다(jdbc);
        기기를_저장한다(jdbc);
        jdbc.sql("UPDATE devices SET nickname = 'Stark' WHERE id = (SELECT min(id) FROM devices)").update();

        // when / then
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE devices SET nickname = :nickname WHERE id = (SELECT max(id) FROM devices)
                """).param("nickname", nickname).update()).isInstanceOf(DuplicateKeyException.class);
    }

    private List<Map<String, Object>> 기기_기록(JdbcClient client) {
        return client.sql("SELECT id, public_id, request_id, platform, created_at, updated_at FROM devices ORDER BY id")
                .query().listOfRows();
    }

    private void 기기를_저장한다(JdbcClient client) {
        client.sql("""
                INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'IOS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).update();
    }
}
