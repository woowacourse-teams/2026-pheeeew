package com.pheeeew.pressrace.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.PheeeewApplication;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.support.SharedTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 HTTP 서버의 종료·재부팅과 세대 전환을 검증한다. Docker 프로세스 재시작은 별도 검증이다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PressHttpDeploymentLifecycleIntegrationTest {

    private static final UUID GREEN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID NEXT = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final String ACTIVATION_DATE = "2026-10-27";
    private static final String PYTHON_PROBE = """
            import importlib.util, os, sys
            sys.dont_write_bytecode = True
            spec = importlib.util.spec_from_file_location('press_generation', sys.argv[1])
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            env = {key: os.environ[key] for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_PRIVATE_KEY_BASE64')}
            print(module.probe_generation(env, ['curl'], sys.argv[2]))
            """;

    private final JsonMapper json = JsonMapper.builder().build();

    private PostgreSQLContainer postgis;
    private String database;
    private String jdbcUrl;
    private KeyPair signingKeys;

    @TempDir
    private static Path tools;

    @BeforeAll
    void setUpDatabase() throws Exception {
        postgis = SharedTestContainers.postgis();
        database = "press_http_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(postgis.getJdbcUrl(),
                postgis.getUsername(), postgis.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE " + database);
        }
        jdbcUrl = "jdbc:postgresql://" + postgis.getHost() + ":" + postgis.getMappedPort(5432) + "/" + database;
        signingKeys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Path psql = tools.resolve("psql");
        Files.writeString(psql, """
                #!/bin/sh
                exec docker exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGOPTIONS %s psql "$@"
                """.formatted(postgis.getContainerId()));
        assertThat(psql.toFile().setExecutable(true)).isTrue();
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
    void 최초_지정과_같은_세대_재부팅_정상_교체_실패_복귀_후에도_HTTP_입력이_저장된다() throws Exception {
        Device device;
        String token;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            // given: 새 앱의 부팅은 초기 NULL 세대를 획득하지 않는다.
            try (ConfigurableApplicationContext initial = start(null)) {
                JdbcClient jdbc = initial.getBean(JdbcClient.class);
                assertThat(owner(jdbc)).isNull();
                jdbc.sql("UPDATE press_race_control SET activation_date = DATE '2026-10-27' WHERE id = TRUE").update();
                device = initial.getBean(DeviceRepository.class).saveAndFlush(기본_기기_빌더().build());
                token = initial.getBean(AccessTokenIssuer.class).issue(device.getPublicId()).accessToken();
                UUID legacyGeneration = initial.getBean(PressRaceService.class).serverGeneration();
                Map<String, Long> rowsBeforeProbe = rowCounts(jdbc);
                assertThat(probe(initial)).isEqualTo(legacyGeneration.toString());
                assertThat(rowCounts(jdbc)).isEqualTo(rowsBeforeProbe);
                assertRejected(press(client, initial, token));
                assertTotals(jdbc, device, 0);

                // when: OFF·날짜·예상 소유권이 일치할 때만 최초 세대를 지정한다.
                changeOwner(jdbc, null, legacyGeneration);

                // then
                assertApplied(press(client, initial, token), 1);
                assertTotals(jdbc, device, 1);

                // 명시적 세대 후보는 새 UUID를 사용한다. 구 앱의 임의 UUID를 복제하지 않는다.
                try (ConfigurableApplicationContext migrated = start(GREEN)) {
                    assertThat(probe(migrated)).isEqualTo(GREEN.toString());
                    assertRejected(press(client, migrated, token));
                    changeOwner(jdbc, legacyGeneration, GREEN);
                    assertRejected(press(client, initial, token));
                    assertApplied(press(client, migrated, token), 2);
                    assertTotals(jdbc, device, 2);
                }
            }

            // when: DB와 명시적 세대 설정을 보존하고 실제 HTTP 서버를 종료·재부팅한다.
            try (ConfigurableApplicationContext restarted = start(GREEN)) {
                JdbcClient jdbc = restarted.getBean(JdbcClient.class);
                assertThat(owner(jdbc)).isEqualTo(GREEN);
                assertApplied(press(client, restarted, token), 3);
                assertTotals(jdbc, device, 3);

                try (ConfigurableApplicationContext candidate = start(NEXT)) {
                    // then: 후보의 부팅·health 성공만으로는 저장 자격을 얻지 못한다.
                    assertThat(owner(jdbc)).isEqualTo(GREEN);
                    assertRejected(press(client, candidate, token));
                    assertTotals(jdbc, device, 3);

                    // when: 새 서버로 정상 전환하면 기존 HTTP는 차단하고 새 HTTP는 저장한다.
                    changeOwner(jdbc, GREEN, NEXT);
                    assertRejected(press(client, restarted, token));
                    assertApplied(press(client, candidate, token), 4);
                    assertTotals(jdbc, device, 4);

                    // when: 전환 실패를 가정해 원래 소유권을 조건부 복구한다.
                    changeOwner(jdbc, NEXT, GREEN);

                    // then: 복귀한 서버에서 기존 누적값을 유지하며 저장하고 후보는 차단한다.
                    assertRejected(press(client, candidate, token));
                    assertApplied(press(client, restarted, token), 5);
                    assertTotals(jdbc, device, 5);
                    assertThat(jdbc.sql("SELECT enabled FROM press_race_control WHERE id = TRUE")
                            .query(Boolean.class).single()).isFalse();
                    assertThat(jdbc.sql("SELECT activation_date::text FROM press_race_control WHERE id = TRUE")
                            .query(String.class).single()).isEqualTo(ACTIVATION_DATE);
                }
            }
        }
    }

    private ConfigurableApplicationContext start(UUID generation) throws Exception {
        SpringApplication application = new SpringApplication(PheeeewApplication.class);
        application.setRegisterShutdownHook(false);
        application.setLogStartupInfo(false);
        Map<String, Object> fixture = Map.ofEntries(
                Map.entry("spring.config.location", "classpath:/application.yml"),
                Map.entry("spring.profiles.active", "test"),
                Map.entry("spring.datasource.url", jdbcUrl),
                Map.entry("spring.datasource.username", postgis.getUsername()),
                Map.entry("spring.datasource.password", postgis.getPassword()),
                Map.entry("JWT_PRIVATE_KEY_BASE64", Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded())),
                Map.entry("JWT_PUBLIC_KEY_BASE64", Base64.getEncoder().encodeToString(signingKeys.getPublic().getEncoded())),
                Map.entry("S3_BUCKET", "pheeeew-test"),
                Map.entry("S3_KEY_PREFIX", "pheeeew/test/"),
                Map.entry("S3_REGION", "ap-northeast-2"),
                Map.entry("pheeeew.map-cache.maintenance.automatic-start", false),
                Map.entry("logging.level.root", "WARN")
        );
        // 테스트 프로세스의 환경변수·JVM 옵션이 실제 DB나 외부 프로필을 선택하지 못하게 한다.
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("pressHttpIsolatedFixture", fixture));
        application.setEnvironment(environment);
        application.addInitializers(context -> {
            // Bean 생성과 Flyway 실행 전에 최종 설정도 다시 확인한다.
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(jdbcUrl);
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("test");
        });
        ConfigurableApplicationContext context = application.run("--server.port=0", "--server.address=127.0.0.1",
                "--pheeeew.press-race.server-generation=" + (generation == null ? "" : generation));
        try (Connection connection = context.getBean(DataSource.class).getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(jdbcUrl);
        } catch (Exception | AssertionError failure) {
            context.close();
            throw failure;
        }
        return context;
    }

    private String probe(ConfigurableApplicationContext application) throws Exception {
        int port = application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        ProcessBuilder builder = new ProcessBuilder("python3", "-c", PYTHON_PROBE,
                Path.of("docker/press-generation.py").toAbsolutePath().toString(),
                "http://127.0.0.1:" + port + "/api/v1/press-race");
        Map<String, String> environment = builder.environment();
        environment.put("PATH", tools + ":" + environment.get("PATH"));
        environment.put("DB_URL", jdbcUrl + "?sslmode=disable");
        environment.put("DB_USERNAME", postgis.getUsername());
        environment.put("DB_PASSWORD", postgis.getPassword());
        environment.put("JWT_PRIVATE_KEY_BASE64", Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded()));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("세대 확인 도구 종료 상한").isTrue();
            assertThat(process.exitValue()).as("실제 WS 오류 봉투로 런타임 세대 확인").isZero();
            return new String(process.getInputStream().readAllBytes()).strip();
        } finally {
            process.destroyForcibly();
        }
    }

    private Map<String, Long> rowCounts(JdbcClient jdbc) {
        return Map.of(
                "devices", jdbc.sql("SELECT COUNT(*) FROM devices").query(Long.class).single(),
                "controls", jdbc.sql("SELECT COUNT(*) FROM press_device_controls").query(Long.class).single(),
                "inputs", jdbc.sql("SELECT COUNT(*) FROM press_inputs").query(Long.class).single(),
                "presses", jdbc.sql("SELECT COUNT(*) FROM device_daily_presses").query(Long.class).single(),
                "scores", jdbc.sql("SELECT COUNT(*) FROM press_daily_scores").query(Long.class).single()
        );
    }

    private UUID owner(JdbcClient jdbc) {
        return (UUID) jdbc.sql("SELECT active_server_generation FROM press_race_control WHERE id = TRUE")
                .query().singleRow().get("active_server_generation");
    }

    private void changeOwner(JdbcClient jdbc, UUID expected, UUID replacement) {
        assertThat(jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :replacement
                WHERE id = TRUE AND NOT enabled AND activation_date = DATE '2026-10-27'
                    AND active_server_generation IS NOT DISTINCT FROM CAST(:expected AS uuid)
                """).param("expected", expected).param("replacement", replacement).update()).isOne();
    }

    private HttpResponse<String> press(HttpClient client, ConfigurableApplicationContext application, String token)
            throws Exception {
        int port = application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v2/emotions/presses"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"counts\":{\"ANGRY\":1}}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertApplied(HttpResponse<String> response, long total) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("total").longValue()).isEqualTo(total);
        assertThat(body.path("counts").path("ANGRY").longValue()).isEqualTo(total);
    }

    private void assertRejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
        assertThat(json.readTree(response.body()).path("code").stringValue()).isEqualTo("SERVER_REPLACED");
    }

    private void assertTotals(JdbcClient jdbc, Device device, long expected) {
        assertThat(jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single()).isEqualTo(expected);
        assertThat(jdbc.sql("SELECT COALESCE(SUM(total), 0) FROM press_daily_scores WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single()).isEqualTo(expected);
    }
}
