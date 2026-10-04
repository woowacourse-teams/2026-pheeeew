package com.pheeeew.region;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.region.fixture.RegionFixture;
import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SgisBoundaryVerificationIntegrationTest {

    private static final Path SCRIPT = Path.of("scripts/regions/verify.sql");
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        RegionFixture.검증용_지역_계층을_저장한다(jdbc);
        postgis().copyFileToContainer(MountableFile.forHostPath(SCRIPT), "/tmp/sgis-verify.sql");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
    }

    @Test
    void 경계선_접촉을_허용하고_성공한_경계만_검증_완료로_표시한다() throws Exception {
        // given
        다른_행정동을_저장한다("MULTIPOLYGON(((128 37,129 37,129 39,128 39,128 37)))");

        // when
        var result = verify("2");

        // then
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        assertThat(검증_시각()).isNotNull();
        assertThat(jdbc.sql("SELECT backfill_verified_at IS NULL FROM region_datasets")
                .query(Boolean.class).single()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2"})
    void 양수가_아니거나_원본_개수와_다르면_검증_완료로_표시하지_않는다(String count) throws Exception {
        // given / when
        var result = verify(count);

        // then
        assertThat(result.getExitCode()).isNotZero();
        assertThat(검증_시각()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MULTIPOLYGON(((127 37,129 37,129 39,127 39,127 37)))",
            "MULTIPOLYGON(((126 37,128 37,128 39,126 39,126 37)))",
            "MULTIPOLYGON(((126.5 37.5,127.5 37.5,127.5 38.5,126.5 38.5,126.5 37.5)))"
    })
    void 부분_겹침과_동일_도형과_완전_포함을_거부한다(String boundary) throws Exception {
        // given
        다른_행정동을_저장한다(boundary);

        // when / then
        assertThat(verify("2").getExitCode()).isNotZero();
        assertThat(검증_시각()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"9.95, true", "9.8, false"})
    void 승인한_쌍도_1제곱미터_상한_이내일_때만_허용한다(double offset, boolean accepted) throws Exception {
        // given: 약 0.5㎡ 또는 2㎡가 겹치는 승인 코드 쌍이다.
        승인_코드_쌍을_저장한다(사각형(0, 0, 10, 10), 사각형(offset, 0, offset + 10, 10));
        double area = 겹침_면적();
        assertThat(area).isBetween(accepted ? 0.49 : 1.99, accepted ? 0.51 : 2.01);

        // when
        var result = verify("2");

        // then
        if (accepted) {
            assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
            assertThat(검증_시각()).isNotNull();
        } else {
            assertThat(result.getExitCode()).isNotZero();
            assertThat(검증_시각()).isNull();
        }
    }

    @Test
    void 목록에_없는_쌍은_1제곱미터_미만이어도_거부한다() throws Exception {
        // given
        승인_코드_쌍을_저장한다(사각형(0, 0, 10, 10), 사각형(9.95, 0, 19.95, 10));
        jdbc.sql("UPDATE regions SET code = '23090610' WHERE code = '23090600'").update();
        assertThat(겹침_면적()).isBetween(0.49, 0.51);

        // when / then
        assertThat(verify("2").getExitCode()).isNotZero();
        assertThat(검증_시각()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 승인한_쌍의_작은_도형도_완전_포함과_동일_도형은_거부한다(boolean identical) throws Exception {
        // given: 승인 코드 쌍이고 교집합 면적도 1㎡보다 작다.
        String first = 사각형(0, 0, 0.5, 0.5);
        승인_코드_쌍을_저장한다(first, identical ? first : 사각형(0.1, 0.1, 0.2, 0.2));
        assertThat(겹침_면적()).isStrictlyBetween(0.0, 1.0);

        // when / then
        assertThat(verify("2").getExitCode()).isNotZero();
        assertThat(검증_시각()).isNull();
    }

    @Test
    void 동시_검증은_메타_행_잠금을_기다리고_최초_검증_시각을_보존한다() throws Exception {
        // given: 실제 검증 SQL의 첫 트랜잭션은 아직 커밋하지 않는다.
        try (var executor = Executors.newSingleThreadExecutor(); var first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.execute(Files.readString(SCRIPT).replace(":'sido_count'", "'1'")
                        .replace(":'sigungu_count'", "'1'").replace(":'emd_count'", "'1'"));
                var pidResult = statement.executeQuery("SELECT pg_backend_pid()");
                pidResult.next();
                int pid = pidResult.getInt(1);
                var timestamp = statement.executeQuery("SELECT boundaries_verified_at::text FROM region_datasets");
                timestamp.next();
                String firstTimestamp = timestamp.getString(1);

                // when: PostgreSQL에서 두 번째 실행의 실제 잠금 대기를 확인한다.
                var second = executor.submit(() -> verify("1"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting;
                do {
                    waiting = jdbc.sql("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE :pid = ANY(pg_blocking_pids(pid)))")
                            .param("pid", pid).query(Boolean.class).single();
                    if (!waiting) {
                        Thread.sleep(10);
                    }
                } while (!waiting && System.nanoTime() < deadline);
                assertThat(waiting).isTrue();
                first.commit();

                // then
                assertThat(second.get(5, TimeUnit.SECONDS).getExitCode()).isNotZero();
                assertThat(검증_시각()).isEqualTo(firstTimestamp);
            } finally {
                first.rollback();
            }
        }
    }

    private ExecResult verify(String emdCount) throws Exception {
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-v", "sido_count=1",
                "-v", "sigungu_count=1", "-v", "emd_count=" + emdCount, "-f", "/tmp/sgis-verify.sql");
    }

    private void 다른_행정동을_저장한다(String boundary) {
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT '11010540', 'EMD', '검증용 다른 행정동', '11010', boundary, ST_PointOnSurface(boundary)
                FROM (SELECT ST_GeomFromText(:boundary, 4326) AS boundary) source
                """).param("boundary", boundary).update();
    }

    private String 검증_시각() {
        return jdbc.sql("SELECT boundaries_verified_at::text FROM region_datasets")
                .query(String.class).optional().orElse(null);
    }

    private void 승인_코드_쌍을_저장한다(String first, String second) {
        jdbc.sql("DELETE FROM regions").update();
        for (String[] region : new String[][]{{"23", "SIDO", null}, {"23090", "SIGUNGU", "23"},
                {"23090590", "EMD", "23090"}, {"23090600", "EMD", "23090"}}) {
            String polygon = region[0].equals("23090590") ? first
                    : region[0].equals("23090600") ? second : 사각형(-10, -10, 30, 30);
            jdbc.sql("""
                    INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                    SELECT :code, :level, '합성 예외 검증 지역', :parent, boundary, ST_PointOnSurface(boundary)
                    FROM (SELECT ST_Multi(ST_Transform(ST_GeomFromText(:polygon, 5179), 4326)) AS boundary) source
                    """).param("code", region[0]).param("level", region[1]).param("parent", region[2])
                    .param("polygon", polygon).update();
        }
    }

    private String 사각형(double minX, double minY, double maxX, double maxY) {
        return "MULTIPOLYGON(((" + (1000000 + minX) + " " + (2000000 + minY) + ","
                + (1000000 + maxX) + " " + (2000000 + minY) + ","
                + (1000000 + maxX) + " " + (2000000 + maxY) + ","
                + (1000000 + minX) + " " + (2000000 + maxY) + ","
                + (1000000 + minX) + " " + (2000000 + minY) + ")))";
    }

    private double 겹침_면적() {
        return jdbc.sql("""
                SELECT ST_Area(ST_Transform(ST_Intersection(a.boundary, b.boundary), 5179))
                FROM regions a JOIN regions b
                  ON a.level = 'EMD' AND b.level = 'EMD' AND a.code < b.code
                """).query(Double.class).single();
    }
}
