package com.pheeeew.region;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.utility.MountableFile;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SgisBoundaryImportIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @TempDir
    private Path source;

    @BeforeAll
    static void checkPreparationTools() throws Exception {
        var loader = run("bash", "-c", "command -v shp2pgsql");
        assertThat(loader.exitCode()).withFailMessage("준비 환경에 shp2pgsql이 필요합니다.").isZero();
        var python = run("python3", "-c", "import pyproj, shapefile");
        assertThat(python.exitCode()).withFailMessage(python.output()).isZero();
    }

    @BeforeEach
    void setUp() throws Exception {
        fixture("11010530", "5179", "UTF-8");
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
    }

    @Test
    void 원본_좌표를_변환하고_한글_이름과_지역_계층을_적재한다() throws Exception {
        // given / when: 합성 SHP를 실제 준비 스크립트와 psql로 읽는다.
        assertThat(prepare().exitCode()).isZero();
        var result = importSql();

        // then
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        assertThat(jdbc.sql("SELECT code || ':' || COALESCE(parent_code, '-') || ':' || name "
                + "FROM regions ORDER BY code").query(String.class).list())
                .containsExactly("11:-:검증 시도", "11010:11:검증 시군구", "11010530:11010:검증 행정동");
        assertThat(jdbc.sql("""
                SELECT ST_SRID(boundary) = 4326 AND GeometryType(boundary) = 'MULTIPOLYGON'
                  AND abs(ST_XMin(boundary) - 127.5) < 0.001
                  AND abs(ST_YMin(boundary) - 38) < 0.001 AND ST_Covers(boundary, display_point)
                FROM regions WHERE code = '11010530'
                """).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT boundaries_verified_at IS NULL AND backfill_verified_at IS NULL "
                + "FROM region_datasets").query(Boolean.class).single()).isTrue();
        staging은_남지_않는다();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 기존_경계와_검증된_데이터셋은_재적재하지_않는다(boolean verified) throws Exception {
        // given
        assertThat(prepare().exitCode()).isZero();
        assertThat(importSql().getExitCode()).isZero();
        if (verified) {
            jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        }

        // when / then
        assertThat(importSql().getExitCode()).isNotZero();
        assertThat(jdbc.sql("SELECT count(*) FROM regions").query(Long.class).single()).isEqualTo(3);
        staging은_남지_않는다();
    }

    @Test
    void 마지막_행정동_적재가_실패하면_앞선_지역과_staging도_롤백한다() throws Exception {
        // given: 존재하지 않는 부모 코드를 가진 합성 SHP다.
        fixture("21010530", "5179", "UTF-8");
        assertThat(prepare().exitCode()).isZero();

        // when / then
        assertThat(importSql().getExitCode()).isNotZero();
        assertThat(jdbc.sql("SELECT count(*) FROM regions").query(Long.class).single()).isZero();
        staging은_남지_않는다();
    }

    @ParameterizedTest
    @ValueSource(strings = {"4326", "CP949"})
    void 다른_원본_좌표계와_인코딩은_SQL_준비부터_거부한다(String invalid) throws Exception {
        // given
        fixture("11010530", invalid.equals("4326") ? "4326" : "5179",
                invalid.equals("CP949") ? "CP949" : "UTF-8");

        // when / then
        assertThat(prepare().exitCode()).isNotZero();
        assertThat(source.resolve("import.sql")).doesNotExist();
    }

    private CommandResult prepare() throws Exception {
        return run("bash", "scripts/regions/prepare-import.sh", source.toString(), source.resolve("import.sql").toString());
    }

    private org.testcontainers.containers.Container.ExecResult importSql() throws Exception {
        postgis().copyFileToContainer(MountableFile.forHostPath(source.resolve("import.sql")), "/tmp/sgis-import.sql");
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-f", "/tmp/sgis-import.sql");
    }

    private void staging은_남지_않는다() {
        assertThat(jdbc.sql("SELECT count(*) FROM pg_namespace WHERE nspname = 'sgis_import'")
                .query(Long.class).single()).isZero();
    }

    private void fixture(String emdCode, String epsg, String encoding) throws Exception {
        var result = run("python3", "-c", """
                import sys, shapefile
                from pathlib import Path
                from pyproj import CRS
                for level, code_field, name_field, code, name in [
                    ('sido', 'SIDO_CD', 'SIDO_NM', '11', '검증 시도'),
                    ('sigungu', 'SIGUNGU_CD', 'SIGUNGU_NM', '11010', '검증 시군구'),
                    ('dong', 'ADM_CD', 'ADM_NM', sys.argv[1], '검증 행정동')]:
                    base = str(Path(sys.argv[4]) / ('bnd_' + level + '_00_2025_2Q'))
                    with shapefile.Writer(base, shapeType=5, encoding=sys.argv[3]) as writer:
                        writer.field(code_field, 'C', 8); writer.field(name_field, 'C', 40)
                        writer.poly([[(1000000,2000000),(1000000,2001000),(1001000,2001000),
                                      (1001000,2000000),(1000000,2000000)]])
                        writer.record(code, name)
                    Path(base + '.prj').write_text(CRS.from_epsg(sys.argv[2]).to_wkt(version='WKT1_ESRI'))
                    Path(base + '.cpg').write_text(sys.argv[3])
                """, emdCode, epsg, encoding, source.toString());
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
    }

    private static CommandResult run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.waitFor(), output);
    }

    private record CommandResult(int exitCode, String output) {
    }
}
