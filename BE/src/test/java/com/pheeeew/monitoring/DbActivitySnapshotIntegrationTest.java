package com.pheeeew.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.SharedTestContainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

class DbActivitySnapshotIntegrationTest {

    private final List<Connection> connections = new ArrayList<>();
    private final String fixture = "observer_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeEach
    void isolateDatabase() throws Exception {
        // 전체 DB의 세션 수를 검사하므로 다른 테스트의 캐시된 연결도 격리한다.
        try (var admin = connect(false)) { execute(admin, "CREATE DATABASE " + fixture); }
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Connection connection : connections) {
            connection.close();
        }
        try (var admin = connect(false); var statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + fixture);
            statement.execute("DROP ROLE IF EXISTS " + fixture);
        }
    }

    @Test
    void 잠금_대기와_유휴_트랜잭션을_구분하고_blocker와_출력_필드를_제한한다() throws Exception {
        // given: 격리된 합성 테이블에 여섯 개의 공유 잠금을 잡는다.
        var admin = connection();
        execute(admin, "CREATE TABLE " + fixture + " (id integer PRIMARY KEY)");
        execute(admin, "INSERT INTO " + fixture + " VALUES (1)");
        var observer = observer();
        assertThat(rows(snapshot(observer))).isEmpty();
        List<Connection> holders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            var holder = connection();
            holder.setAutoCommit(false);
            execute(holder, "LOCK TABLE " + fixture + " IN SHARE MODE /* secret-coordinate-token */");
            holders.add(holder);
        }
        var waiter = connection();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var update = executor.submit(() -> { execute(waiter, "UPDATE " + fixture + " SET id=2"); return true; });
            try {
                // when: 시간 제한 안에서 실제 Lock 대기 표본이 나타나는 것을 기다린다.
                Map<String, Object> sample = snapshot(observer);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (rows(sample).stream().noneMatch(row -> "Lock".equals(row.get("wait_event_type")))) {
                    assertThat(System.nanoTime()).isLessThan(deadline);
                    Thread.sleep(20);
                    sample = snapshot(observer);
                }
                // then: SQL 원문 없이 대기 및 blocker 출력 상한을 보존한다.
                var blocked = rows(sample).stream().filter(row -> "Lock".equals(row.get("wait_event_type"))).findFirst().orElseThrow();
                assertThat(((Number) sample.get("lock_wait_count")).intValue()).isEqualTo(1);
                assertThat(((Number) sample.get("active_count")).intValue()).isEqualTo(1);
                assertThat(((Number) sample.get("idle_in_transaction_count")).intValue()).isEqualTo(6);
                assertThat(((Number) sample.get("other_wait_count")).intValue()).isEqualTo(6);
                assertThat(((Number) sample.get("io_wait_count")).intValue()).isZero();
                assertThat(((Number) sample.get("no_wait_count")).intValue()).isZero();
                assertThat(((Number) blocked.get("blocker_count")).intValue()).isEqualTo(6);
                assertThat((List<?>) blocked.get("blocker_pids")).hasSize(5);
                assertThat(((Number) blocked.get("query_elapsed_ms")).doubleValue()).isGreaterThanOrEqualTo(0);
                assertThat(blocked.keySet()).containsExactlyInAnyOrder("pid", "backend_start", "backend_type", "state",
                        "wait_event_type", "wait_event", "query_elapsed_ms", "transaction_elapsed_ms", "state_elapsed_ms",
                        "blocker_pids", "blocker_count");
                assertThat(sample.toString()).doesNotContain("secret-coordinate-token", fixture);
                assertThat(rows(sample).stream().filter(row -> "idle in transaction".equals(row.get("state"))))
                        .hasSize(6).allSatisfy(row -> assertThat(row.get("query_elapsed_ms")).isNull());
            } finally {
                for (var holder : holders) { holder.rollback(); }
            }
            assertThat(update.get(3, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(rows(snapshot(observer))).isEmpty();
    }

    @Test
    void 세션_상한과_활동_추적_중단을_빈_결과로_숨기지_않는다() throws Exception {
        // given
        var observer = observer();
        for (int i = 0; i < 21; i++) {
            var worker = connection();
            worker.setAutoCommit(false);
            execute(worker, "SELECT 1");
        }
        // when / then
        var sample = snapshot(observer);
        assertThat(rows(sample)).hasSize(20);
        assertThat(((Number) sample.get("eligible_count")).intValue()).isEqualTo(21);
        assertThat(((Number) sample.get("omitted_count")).intValue()).isEqualTo(1);
        assertThat(((Number) sample.get("idle_in_transaction_count")).intValue()).isEqualTo(21);
        assertThat(((Number) sample.get("other_wait_count")).intValue()).isEqualTo(21);
        assertThat(((Number) sample.get("active_count")).intValue()).isZero();
        execute(connections.getLast(), "SET track_activities=off");
        sample = snapshot(observer);
        assertThat(sample.get("visibility")).isEqualTo("partial");
        assertThat(((Number) sample.get("disabled_count")).intValue()).isEqualTo(1);
        assertThat(((Number) sample.get("eligible_count")).intValue()).isEqualTo(20);
        assertThat(((Number) sample.get("idle_in_transaction_count")).intValue()).isEqualTo(20);
        assertThat(((Number) sample.get("other_wait_count")).intValue()).isEqualTo(20);
    }

    @Test
    void 다른_역할의_가려진_세션을_관측_가능한_빈_결과와_구분한다() throws Exception {
        // given
        execute(connection(), "CREATE ROLE " + fixture);
        var observer = observer();
        execute(observer, "SET ROLE " + fixture);
        // when / then: 다른 역할 세션이 idle이어도 LIMIT 이전 모집단에서 가시성을 판단한다.
        var sample = snapshot(observer);
        assertThat(sample.get("visibility")).isEqualTo("partial");
        assertThat(sample.get("read_all_stats")).isEqualTo(false);
        assertThat(((Number) sample.get("unknown_state_count")).intValue()).isPositive();
        assertThat(rows(sample)).isEmpty();
        for (String field : List.of("active_count", "idle_in_transaction_count", "lock_wait_count",
                "io_wait_count", "other_wait_count", "no_wait_count")) {
            assertThat(((Number) sample.get(field)).intValue()).isZero();
        }
    }

    private Connection connect(boolean isolated) throws Exception {
        var db = SharedTestContainers.postgis();
        String url = isolated ? db.getJdbcUrl().replace("/" + db.getDatabaseName(), "/" + fixture) : db.getJdbcUrl();
        return DriverManager.getConnection(url, db.getUsername(), db.getPassword());
    }

    private Connection connection() throws Exception {
        var connection = connect(true);
        connections.add(connection);
        execute(connection, "SET statement_timeout='5s'");
        return connection;
    }

    private Connection observer() throws Exception {
        var observer = connection();
        execute(observer, "SET default_transaction_read_only=on; SET statement_timeout='500ms'");
        return observer;
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private Map<String, Object> snapshot(Connection observer) throws Exception {
        try (var statement = observer.createStatement(); var result = statement.executeQuery(
                Files.readString(Path.of("docker/db-observer/snapshot.sql")))) {
            assertThat(result.next()).isTrue();
            return JsonParserFactory.getJsonParser().parseMap(result.getString(1));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> sample) {
        return (List<Map<String, Object>>) sample.get("sessions");
    }
}
