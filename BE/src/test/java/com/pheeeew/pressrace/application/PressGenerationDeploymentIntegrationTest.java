package com.pheeeew.pressrace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.SharedTestContainers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 배포 도구의 실제 SQL을 기존 PostGIS Testcontainers에서 실행한다. 운영 DB나 앱 프로세스는 사용하지 않는다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PressGenerationDeploymentIntegrationTest {

    private static final UUID BLUE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GREEN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String PYTHON_COMMAND = """
            import importlib.util, json, os, sys
            sys.dont_write_bytecode = True
            spec = importlib.util.spec_from_file_location('press_generation', sys.argv[1])
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            env = {key: os.environ[key] for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')}
            try:
                if sys.argv[2] == 'change':
                    values = [None if value == '-' else value for value in sys.argv[3:6]]
                    module.change(env, *values)
                print(json.dumps(module.read_state(env)))
            except ValueError as error:
                print(str(error), file=sys.stderr)
                sys.exit(2)
            """;

    @TempDir
    private static Path tools;

    private PostgreSQLContainer postgis;
    private String database;
    private String jdbcUrl;

    @BeforeAll
    void setUpDatabase() throws Exception {
        postgis = SharedTestContainers.postgis();
        database = "press_generation_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(postgis.getJdbcUrl(),
                postgis.getUsername(), postgis.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE " + database);
        }
        jdbcUrl = "jdbc:postgresql://" + postgis.getHost() + ":" + postgis.getMappedPort(5432) + "/" + database;
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V20261026_2__create_press_race_storage.sql"));
        try (Connection connection = connection()) {
            connection.createStatement().execute(migration.substring(0, migration.indexOf("CREATE TABLE press_device_controls")));
        }
        // Preserve the helper's psql protocol and timeouts, replacing only the client location.
        Path psql = tools.resolve("psql");
        Files.writeString(psql, """
                #!/bin/sh
                exec docker exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGOPTIONS -e PGAPPNAME %s psql "$@"
                """.formatted(postgis.getContainerId()));
        assertThat(psql.toFile().setExecutable(true)).isTrue();
    }

    @BeforeEach
    void resetControl() throws Exception {
        try (Connection connection = connection()) {
            connection.createStatement().execute("""
                    UPDATE press_race_control SET active_server_generation = NULL,
                        enabled = false, activation_date = NULL
                    """);
        }
    }

    @AfterAll
    void tearDownDatabase() throws Exception {
        if (database != null) {
            try (Connection connection = DriverManager.getConnection(postgis.getJdbcUrl(),
                    postgis.getUsername(), postgis.getPassword())) {
                connection.createStatement().execute("DROP DATABASE " + database + " WITH (FORCE)");
            }
        }
    }

    @Test
    void 초기_서버를_지정하고_복귀해도_경쟁_OFF와_활성화_날짜를_유지한다() throws Exception {
        // given
        try (Connection connection = connection()) {
            connection.createStatement().execute("UPDATE press_race_control SET activation_date = DATE '2026-10-27'");
        }

        // when
        CommandResult assigned = change(null, GREEN, "2026-10-27");

        // then
        assertSuccess(assigned);
        assertThat(assigned.output()).contains(GREEN.toString(), "\"enabled\": false", "2026-10-27");
        CommandResult restored = change(GREEN, null, "2026-10-27");
        assertSuccess(restored);
        assertThat(restored.output()).contains("\"generation\": null", "\"enabled\": false", "2026-10-27");
    }

    @Test
    void 서버를_전환하고_복귀하며_다른_소유자의_세대를_덮어쓰지_않는다() throws Exception {
        // given
        assertSuccess(change(null, BLUE, null));

        // when
        assertSuccess(change(BLUE, GREEN, null));
        CommandResult stale = change(BLUE, null, null);

        // then
        assertThat(stale.exitCode()).isEqualTo(2);
        assertThat(stale.error()).contains("Database operation failed");
        assertThat(generation()).isEqualTo(GREEN.toString());
        assertSuccess(change(GREEN, BLUE, null));
        assertThat(generation()).isEqualTo(BLUE.toString());
    }

    @Test
    void 경쟁이_켜졌거나_활성화_날짜가_바뀌면_서버_자격을_변경하지_않는다() throws Exception {
        // given
        assertSuccess(change(null, BLUE, null));
        try (Connection connection = connection()) {
            connection.createStatement().execute("UPDATE press_race_control SET enabled = true");
        }

        // when & then
        assertThat(change(BLUE, GREEN, null).exitCode()).isEqualTo(2);
        assertThat(generation()).isEqualTo(BLUE.toString());
        try (Connection connection = connection()) {
            connection.createStatement().execute("""
                    UPDATE press_race_control SET enabled = false, activation_date = DATE '2026-10-28'
                    """);
        }
        assertThat(change(BLUE, GREEN, null).exitCode()).isEqualTo(2);
        assertThat(generation()).isEqualTo(BLUE.toString());
    }

    @Test
    void 진행_중_저장_트랜잭션의_공유_잠금이_끝난_뒤에만_서버를_교체한다() throws Exception {
        // given
        assertSuccess(change(null, BLUE, null));
        try (Connection saving = connection()) {
            saving.setAutoCommit(false);
            saving.createStatement().execute("SELECT id FROM press_race_control WHERE id = TRUE FOR SHARE");
            Process handoff = startChange(BLUE, GREEN, null);
            try {
                // when
                awaitLockWait();

                // then
                assertThat(handoff.isAlive()).isTrue();
                assertThat(generation()).isEqualTo(BLUE.toString());
                saving.commit();
                assertSuccess(finish(handoff));
                assertThat(generation()).isEqualTo(GREEN.toString());
            } finally {
                handoff.destroyForcibly();
            }
        }
    }

    @Test
    void 공유_잠금_대기가_상한을_넘으면_실패하고_기존_서버_자격을_유지한다() throws Exception {
        // given
        assertSuccess(change(null, BLUE, null));
        try (Connection saving = connection()) {
            saving.setAutoCommit(false);
            saving.createStatement().execute("SELECT id FROM press_race_control WHERE id = TRUE FOR SHARE");

            // when
            long started = System.nanoTime();
            CommandResult rejected = change(BLUE, GREEN, null);

            // then
            assertThat(rejected.exitCode()).isEqualTo(2);
            assertThat(rejected.error()).contains("Database operation failed");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(15));
            assertThat(generation()).isEqualTo(BLUE.toString());
            saving.rollback();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, postgis.getUsername(), postgis.getPassword());
    }

    private String generation() throws Exception {
        try (Connection connection = connection(); ResultSet rows = connection.createStatement().executeQuery(
                "SELECT active_server_generation::text FROM press_race_control WHERE id = TRUE")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private void awaitLockWait() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = connection(); ResultSet rows = connection.createStatement().executeQuery("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE application_name = 'press-generation-deployment-test' AND wait_event_type = 'Lock')
                    """)) {
                rows.next();
                if (rows.getBoolean(1)) {
                    return;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("배포 전환 UPDATE의 DB 잠금 대기를 관찰하지 못했다");
    }

    private CommandResult change(UUID expected, UUID replacement, String activation) throws Exception {
        return finish(startChange(expected, replacement, activation));
    }

    private Process startChange(UUID expected, UUID replacement, String activation) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("python3", "-c", PYTHON_COMMAND,
                Path.of("docker/press-generation.py").toAbsolutePath().toString(), "change",
                argument(expected), argument(replacement), argument(activation));
        Map<String, String> env = builder.environment();
        env.put("PATH", tools + ":" + env.get("PATH"));
        env.put("DB_URL", jdbcUrl + "?sslmode=disable");
        env.put("DB_USERNAME", postgis.getUsername());
        env.put("DB_PASSWORD", postgis.getPassword());
        env.put("PGAPPNAME", "press-generation-deployment-test");
        return builder.start();
    }

    private String argument(Object value) {
        return value == null ? "-" : value.toString();
    }

    private CommandResult finish(Process process) throws Exception {
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("배포 도구 종료 상한").isTrue();
            return CommandResult.of(process.exitValue(), new String(process.getInputStream().readAllBytes()),
                    new String(process.getErrorStream().readAllBytes()));
        } finally {
            process.destroyForcibly();
        }
    }

    private void assertSuccess(CommandResult result) {
        assertThat(result.exitCode()).as(result.error()).isZero();
    }

    private record CommandResult(int exitCode, String output, String error) {

        private static CommandResult of(int exitCode, String output, String error) {
            return new CommandResult(exitCode, output, error);
        }
    }
}
