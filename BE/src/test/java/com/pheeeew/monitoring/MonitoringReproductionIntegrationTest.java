package com.pheeeew.monitoring;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import io.micrometer.core.instrument.MeterRegistry;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@EnabledIfEnvironmentVariable(named = "MONITORING_REPRO_IMPORT_SQL", matches = ".+")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Import({SharedJwtTestConfiguration.class, MonitoringReproductionIntegrationTest.DatabaseConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pheeeew.s3.bucket=pheeeew-test", "pheeeew.s3.key-prefix=pheeeew/test/",
        "pheeeew.s3.region=ap-northeast-2", "spring.datasource.hikari.maximum-pool-size=10",
        "server.address=127.0.0.1", "management.prometheus.metrics.export.enabled=true"
})
class MonitoringReproductionIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private String monitoringDatabase;
    @Autowired
    private AccessTokenIssuer tokenIssuer;
    @Autowired
    private MeterRegistry registry;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private DataSource dataSource;
    @LocalServerPort
    private int port;

    @Test
    void 실제_경계와_합성_데이터로_API_기준값과_부하_관측을_기록한다() throws Exception {
        String scenario = System.getenv().getOrDefault("MONITORING_REPRO_SCENARIO", "baseline");
        assertThat(scenario).isIn("baseline", "regions", "press");
        // given: 실제 경계 파일만 입력받으며 DB 연결 대상은 Testcontainers 내부에서 결정한다.
        Path source = Path.of(System.getenv("MONITORING_REPRO_IMPORT_SQL"));
        assertThat(Files.isRegularFile(source)).as("준비된 SGIS import SQL").isTrue();
        assertThat(jdbc.sql("SELECT current_database()").query(String.class).single())
                .isEqualTo(monitoringDatabase).startsWith("monitoring_repro_");
        assertThat(jdbc.sql("SHOW server_version").query(String.class).single()).startsWith("17.");
        assertThat(jdbc.sql("SELECT postgis_lib_version()").query(String.class).single()).startsWith("3.5.");

        // when: 대용량 SQL을 JVM 메모리에 읽지 않고 기존 적재·품질 검증 절차를 실행한다.
        executeSql(source);
        executeSql(Path.of("scripts/regions/verify.sql"),
                "-v", "sido_count=17", "-v", "sigungu_count=252", "-v", "emd_count=3559");
        executeSql(Path.of("docker/benchmarks/monitoring-seed.sql"));
        executeSql(Path.of("scripts/regions/backfill-verify.sql"));

        // then: 빈 seed, 잘못된 분류/날짜와 부분 준비를 성공으로 처리하지 않는다.
        assertThat(jdbc.sql("SELECT level || ':' || count(*) FROM regions GROUP BY level ORDER BY level")
                .query(String.class).list()).containsExactly("EMD:3559", "SIDO:17", "SIGUNGU:252");
        assertThat(jdbc.sql("SELECT count(*) FROM devices").query(Long.class).single()).isEqualTo(100);
        assertThat(jdbc.sql("SELECT state || ':' || count(*) FROM emotions GROUP BY state ORDER BY state")
                .query(String.class).list()).containsExactly("ANGRY:2000", "DISCOURAGED:2000", "EXHAUSTED:2000",
                        "FRUSTRATED:2000", "IRRITATED:2000");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM emotions e JOIN regions r ON r.code = e.region_code
                WHERE r.level = 'EMD' AND left(r.code, 2) = '11' AND ST_Covers(r.boundary, e.location)
                  AND e.region_classified_at IS NOT NULL AND e.memo IS NOT NULL
                  AND e.deleted_at IS NULL AND e.audio_object_key IS NULL AND e.anonymous
                """).query(Long.class).single()).isEqualTo(10000);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM device_daily_presses
                WHERE press_date = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AND press_count = 1
                """).query(Long.class).single()).isEqualTo(500);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM app_version WHERE is_active
                  AND platform IN ('ANDROID', 'IOS') AND min_supported_version = '1.0.0'
                  AND latest_version = '1.0.0'
                """).query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("""
                SELECT boundaries_verified_at IS NOT NULL AND backfill_verified_at IS NOT NULL
                FROM region_datasets WHERE dataset_key = 'SGIS_2025_2Q'
                """).query(Boolean.class).single()).isTrue();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
        UUID device = jdbc.sql("SELECT public_id FROM devices ORDER BY id LIMIT 1").query(UUID.class).single();
        String baseUrl = "http://127.0.0.1:" + port;
        String token = tokenIssuer.issue(device).accessToken();
        long expectedPressTotal = 500;
        if (scenario.equals("baseline")) {
            new MonitoringHttpBaseline(baseUrl, token, mapper, registry).run();
            expectedPressTotal = 523;
        } else if (scenario.equals("press")) {
            var devices = jdbc.sql("SELECT public_id FROM devices ORDER BY id LIMIT 32").query(UUID.class).list();
            var tokens = devices.stream().map(id -> tokenIssuer.issue(id).accessToken()).toList();
            var applied = new MonitoringRegionsCompanionLoad(baseUrl, token, mapper, registry,
                    dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean()).runPress(tokens);
            Path output = Path.of("build/reports/monitoring/press-companions.json");
            var report = (ObjectNode) mapper.readTree(output.toFile());
            report.put("databaseVerification", "failed");
            try {
                for (int index = 0; index < devices.size(); index++) {
                    long angry = jdbc.sql("""
                            SELECT p.press_count FROM device_daily_presses p JOIN devices d ON d.id = p.device_id
                            WHERE d.public_id = :device AND p.state = 'ANGRY'
                            """).param("device", devices.get(index)).query(Long.class).single();
                    assertThat(angry).as("기기 %s ANGRY 증가", index).isEqualTo(1 + applied.get(index));
                }
                assertThat(jdbc.sql("SELECT count(*) FROM device_daily_presses WHERE state <> 'ANGRY' AND press_count <> 1")
                        .query(Long.class).single()).isZero();
                assertThat(jdbc.sql("SELECT count(*) FROM device_daily_presses").query(Long.class).single()).isEqualTo(500);
                expectedPressTotal += applied.values().stream().mapToLong(Long::longValue).sum();
                assertThat(jdbc.sql("SELECT sum(press_count) FROM device_daily_presses").query(Long.class).single())
                        .isEqualTo(expectedPressTotal);
                report.put("databaseVerification", "passed");
            } finally {
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            }
        } else {
            new MonitoringRegionsCompanionLoad(baseUrl, token, mapper, registry,
                    dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean()).run();
        }
        if (!scenario.equals("press")) {
            assertThat(jdbc.sql("SELECT sum(press_count) FROM device_daily_presses").query(Long.class).single())
                    .isEqualTo(expectedPressTotal);
        }
    }

    private void executeSql(Path path, String... variables) throws Exception {
        String target = "/tmp/" + monitoringDatabase + "-" + path.getFileName();
        postgis().copyFileToContainer(MountableFile.forHostPath(path.toAbsolutePath()), target);
        var command = new ArrayList<>(List.of("psql", "-U", postgis().getUsername(), "-d", monitoringDatabase,
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-q"));
        command.addAll(List.of(variables));
        command.addAll(List.of("-f", target));
        var result = postgis().execInContainer(command.toArray(String[]::new));
        assertThat(result.getExitCode()).as("%s 적재/검증: %s", path, result.getStderr()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {

        @Bean
        String monitoringDatabase() throws Exception {
            String name = "monitoring_repro_" + UUID.randomUUID().toString().replace("-", "");
            var result = postgis().execInContainer("createdb", "-U", postgis().getUsername(), name);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("재현 DB 생성 실패: " + result.getStderr());
            }
            return name;
        }

        @Bean
        DynamicPropertyRegistrar monitoringDatabaseProperties(String monitoringDatabase) {
            String url = postgis().getJdbcUrl().replace("/" + postgis().getDatabaseName(), "/" + monitoringDatabase);
            return registry -> {
                registry.add("spring.datasource.url", () -> url);
                registry.add("spring.datasource.username", () -> postgis().getUsername());
                registry.add("spring.datasource.password", () -> postgis().getPassword());
            };
        }
    }
}
