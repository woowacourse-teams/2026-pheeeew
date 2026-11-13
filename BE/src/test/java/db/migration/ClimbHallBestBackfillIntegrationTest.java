package db.migration;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ClimbHallBestBackfillIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

    private String database;
    private JdbcClient admin;
    private JdbcClient jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void 명예의_전당까지만_만든_격리_DB를_준비한다() {
        var container = postgis();
        admin = JdbcClient.create(new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword()));
        database = "climb_hall_best_" + UUID.randomUUID().toString().replace("-", "");
        admin.sql("CREATE DATABASE " + database).update();
        dataSource = new DriverManagerDataSource(
                container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database),
                container.getUsername(), container.getPassword());
        jdbc = JdbcClient.create(dataSource);
        Flyway.configure().dataSource(dataSource).target("20261112.91").load().migrate();
    }

    @AfterEach
    void 격리_DB를_제거한다() {
        admin.sql("DROP DATABASE " + database).update();
    }

    @Test
    void 가장_빠른_기록을_의심_완주를_빼고_채우고_처음_완주는_그대로_둔다() {
        // given: 어셔는 2705초로 처음 완주한 뒤 716초, 의심 200초, 느린 1500초로 다시 완주했다.
        UUID usher = 손님("어셔", "aaaa");
        UUID once = 손님("한번", "bbbb");
        UUID gone = UUID.randomUUID();
        UUID first = 완주(usher, 2705, 4, 0, false);
        완주(once, 900, 1, 60, false);
        완주(usher, 716, 2, 3600, false);
        완주(usher, 200, 0, 4000, true);
        완주(usher, 1500, 0, 7000, false);
        jdbc.sql("""
                INSERT INTO climb_hall (run_id, guest_id, map_version, tag, nickname, "character", finish_seconds,
                    falls, finished_at)
                VALUES (gen_random_uuid(), :guest, 'v1', 'cccc', '떠남', 'angry', 1200, 7, :at)
                """).param("guest", gone).param("at", java.sql.Timestamp.from(T0.plusSeconds(30))).update();
        // V91 이후에 들어온 완주는 옛 서버의 syncHall(V91 과 같은 규칙)이 옮겼다.
        jdbc.sql("""
                INSERT INTO climb_hall (run_id, guest_id, map_version, tag, nickname, "character", finish_seconds,
                    falls, finished_at)
                SELECT DISTINCT ON (r.guest_id, r.map_version)
                       r.id, r.guest_id, r.map_version, g.tag, g.nickname, r."character", r.finish_seconds, r.falls,
                       r.finished_at
                FROM climb_run r JOIN climb_guest g ON g.id = r.guest_id
                WHERE r.finished_at IS NOT NULL AND r.finish_seconds IS NOT NULL AND NOT r.suspect
                ORDER BY r.guest_id, r.map_version, r.finished_at
                ON CONFLICT DO NOTHING
                """).update();

        // when
        Flyway.configure().dataSource(dataSource).target("20261113.92").load().migrate();

        // then
        Map<String, Object> mine = 줄(usher);
        assertThat(mine.get("run_id")).isEqualTo(first);
        assertThat(mine.get("finish_seconds")).isEqualTo(2705);
        assertThat(mine.get("falls")).isEqualTo(4);
        assertThat(시각(mine, "finished_at")).isEqualTo(T0);
        assertThat(mine.get("best_seconds")).isEqualTo(716);
        assertThat(mine.get("best_falls")).isEqualTo(2);
        assertThat(시각(mine, "best_finished_at")).isEqualTo(T0.plusSeconds(3600));
        Map<String, Object> single = 줄(once);
        assertThat(single.get("best_seconds")).isEqualTo(900);
        assertThat(single.get("best_falls")).isEqualTo(1);
        assertThat(시각(single, "best_finished_at")).isEqualTo(T0.plusSeconds(60));
        Map<String, Object> left = 줄(gone);
        assertThat(left.get("best_seconds")).isEqualTo(1200);
        assertThat(left.get("best_falls")).isEqualTo(7);
        assertThat(시각(left, "best_finished_at")).isEqualTo(T0.plusSeconds(30));
    }

    private UUID 손님(String nickname, String tag) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO climb_guest (id, tag, nickname, token_hash, created_at, last_seen_at)
                VALUES (:id, :tag, :nickname, :hash, now(), now())
                """).param("id", id).param("tag", tag).param("nickname", nickname)
                .param("hash", id.toString().getBytes()).update();
        return id;
    }

    private UUID 완주(UUID guest, int seconds, int falls, long afterSeconds, boolean suspect) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO climb_run (id, guest_id, map_version, "character", best_height, best_at, height, x, falls,
                    play_seconds, finished_at, finish_seconds, suspect, started_at, updated_at)
                VALUES (:id, :guest, 'v1', 'angry', 663, :at, 663, 0, :falls, :seconds, :at, :seconds, :suspect,
                    :at, :at)
                """).param("id", id).param("guest", guest).param("falls", falls).param("seconds", seconds)
                .param("suspect", suspect).param("at", java.sql.Timestamp.from(T0.plusSeconds(afterSeconds)))
                .update();
        return id;
    }

    private Map<String, Object> 줄(UUID guest) {
        return jdbc.sql("SELECT * FROM climb_hall WHERE guest_id = :guest").param("guest", guest).query().singleRow();
    }

    private static Instant 시각(Map<String, Object> row, String column) {
        return ((java.sql.Timestamp) row.get(column)).toInstant();
    }
}
