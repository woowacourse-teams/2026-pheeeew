package db.migration;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DeviceNicknameBackfillIntegrationTest {

    private String database;
    private JdbcClient admin;
    private JdbcClient jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void 백필_전의_격리_DB를_준비한다() {
        var container = postgis();
        admin = JdbcClient.create(new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        database = "nickname_backfill_" + UUID.randomUUID().toString().replace("-", "");
        admin.sql("CREATE DATABASE " + database).update();
        dataSource = new DriverManagerDataSource(
                container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database),
                container.getUsername(), container.getPassword());
        jdbc = JdbcClient.create(dataSource);
        Flyway.configure().dataSource(dataSource).target("20261007.1").load().migrate();
    }

    @AfterEach
    void 격리_DB를_제거한다() {
        admin.sql("DROP DATABASE " + database).update();
    }

    @Test
    void 자동_탐색한_백필이_한국어_닉네임을_부여하고_기존_정보를_보존한다() {
        // given
        기기를_저장한다(252);
        jdbc.sql("""
                INSERT INTO device_refresh_tokens (device_id, session_id, token_hash, created_at, updated_at)
                SELECT min(id), gen_random_uuid(), repeat('a', 64), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM devices
                """).update();
        List<Map<String, Object>> devices = 기기_기록();
        Map<String, Object> token = jdbc.sql("SELECT * FROM device_refresh_tokens").query().singleRow();

        // when: 운영과 같은 classpath 탐색과 라이브러리의 기본 사전을 사용한다.
        Flyway latest = Flyway.configure().dataSource(dataSource).target("20261007.2").load();
        latest.migrate();

        // then
        List<String> nicknames = jdbc.sql("SELECT nickname FROM devices ORDER BY id").query(String.class).list();
        assertThat(nicknames).hasSize(252).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(nicknames.subList(0, 3)).containsExactly("외로운 너구리", "외로운 고라니", "외로운 토끼");
        assertThat(nicknames.get(171)).isEqualTo("심심한 너구리");
        assertThat(nicknames).allSatisfy(nickname -> {
            assertThat(nickname).matches("[가-힣]+(?: [가-힣]+)+").hasSizeBetween(1, 10);
        });
        assertThat(기기_기록()).isEqualTo(devices);
        assertThat(jdbc.sql("SELECT * FROM device_refresh_tokens").query().singleRow()).isEqualTo(token);

        latest.migrate();
        assertThat(jdbc.sql("SELECT nickname FROM devices ORDER BY id").query(String.class).list())
                .isEqualTo(nicknames);
        기기를_저장한다(1);
        assertThat(jdbc.sql("SELECT count(*) FROM devices WHERE nickname IS NULL").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void 사전이_고갈되면_이미_실행한_배치도_롤백한다() throws IOException {
        // given: 100개 조합을 먼저 저장한 뒤 101번째 기기에서 사전이 고갈된다.
        기기를_저장한다(101);
        Flyway latest = 백필(List.of("같은"), 캐릭터_사전(100));
        List<Map<String, Object>> devices = 기기_기록();

        // when / then
        assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Device nickname backfill exhausted its available combinations");
        assertThat(jdbc.sql("SELECT count(*) FROM devices WHERE nickname IS NULL").query(Long.class).single())
                .isEqualTo(101);
        assertThat(기기_기록()).isEqualTo(devices);
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '20261007.2'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void 후속_배치가_실패하면_이미_실행한_배치도_롤백한다() throws IOException {
        // given: 첫 100건 저장 후 후속 배치에서 DB 제약 위반을 발생시킨다.
        기기를_저장한다(101);
        jdbc.sql("""
                CREATE FUNCTION reject_last_nickname() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.id = (SELECT max(id) FROM devices) THEN
                        NEW.nickname := '익명';
                    END IF;
                    RETURN NEW;
                END $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER reject_last_nickname BEFORE UPDATE ON devices
                FOR EACH ROW EXECUTE FUNCTION reject_last_nickname()
                """).update();
        List<Map<String, Object>> devices = 기기_기록();

        // when / then
        assertThatThrownBy(() -> 백필(List.of("같은"), 캐릭터_사전(101)).migrate())
                .isInstanceOf(FlywayException.class).hasStackTraceContaining("ck_devices_nickname");
        assertThat(jdbc.sql("SELECT count(*) FROM devices WHERE nickname IS NULL").query(Long.class).single())
                .isEqualTo(101);
        assertThat(기기_기록()).isEqualTo(devices);
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '20261007.2'")
                .query(Long.class).single()).isZero();
    }

    private void 기기를_저장한다(int count) {
        jdbc.sql("""
                INSERT INTO devices (public_id, request_id, platform, created_at, updated_at)
                SELECT gen_random_uuid(), gen_random_uuid(), 'ANDROID',
                       CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 hour'
                FROM generate_series(1, :count)
                """).param("count", count).update();
    }

    private List<Map<String, Object>> 기기_기록() {
        return jdbc.sql("SELECT id, public_id, request_id, platform, created_at, updated_at FROM devices ORDER BY id")
                .query().listOfRows();
    }

    private List<String> 캐릭터_사전(int count) {
        return IntStream.range(0, count).mapToObj(index -> String.valueOf((char) ('가' + index))).toList();
    }

    private Flyway 백필(List<String> modifiers, List<String> characters) throws IOException {
        // SQL 이력은 검증하면서 Java migration만 지정한 사전으로 실행한다.
        String sqlDirectory = new ClassPathResource("db/migration/V20261007_1__add_device_nickname.sql")
                .getFile().getParent();
        return Flyway.configure().dataSource(dataSource).locations("filesystem:" + sqlDirectory)
                .javaMigrations(new V20261007_2__backfill_device_nicknames(modifiers, characters))
                .target("20261007.2").load();
    }
}
