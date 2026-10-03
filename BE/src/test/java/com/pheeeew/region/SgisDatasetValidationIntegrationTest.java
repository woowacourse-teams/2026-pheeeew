package com.pheeeew.region;

import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.region.application.RegionClassifier;
import com.pheeeew.region.domain.repository.RegionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.utility.MountableFile;

@PostgisDataJpaTest
@Import({RegionClassifier.class, RegionRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "SGIS_SOURCE_DIRECTORY", matches = ".+")
class SgisDatasetValidationIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private RegionClassifier classifier;
    @TempDir
    private Path output;

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
    }

    @Test
    void 실제_2025년_2분기_전국_경계를_적재하고_품질과_행정동_분류를_검증한다() throws Exception {
        // given: 내려받은 원본을 지정한 환경에서만 실행하며 원본과 운영 DB는 변경하지 않는다.
        Path source = Path.of(System.getenv("SGIS_SOURCE_DIRECTORY"));
        Path sql = output.resolve("sgis-import.sql");
        Process preparation = new ProcessBuilder("bash", "scripts/regions/prepare-import.sh",
                source.toString(), sql.toString()).redirectErrorStream(true).start();
        String preparationOutput = new String(preparation.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(preparation.waitFor()).withFailMessage(preparationOutput).isZero();

        // when: 실제 도구의 단일 트랜잭션 적재와 검증을 격리된 Testcontainers DB에서 실행한다.
        postgis().copyFileToContainer(MountableFile.forHostPath(sql), "/tmp/sgis-dataset-import.sql");
        var imported = postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-f", "/tmp/sgis-dataset-import.sql");
        assertThat(imported.getExitCode()).withFailMessage(imported.getStderr()).isZero();
        postgis().copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/regions/verify.sql")),
                "/tmp/sgis-dataset-verify.sql");
        var verified = postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-v", "sido_count=17",
                "-v", "sigungu_count=252", "-v", "emd_count=3559", "-f", "/tmp/sgis-dataset-verify.sql");

        // then: 기대 개수는 원본 DBF에서 확인한 값이며 실패 결과에 맞춰 조정하지 않는다.
        assertThat(verified.getExitCode()).withFailMessage(verified.getStderr()).isZero();
        assertThat(jdbc.sql("SELECT level || ':' || count(*) FROM regions GROUP BY level ORDER BY level")
                .query(String.class).list()).containsExactly("EMD:3559", "SIDO:17", "SIGUNGU:252");
        assertThat(jdbc.sql("SELECT boundaries_verified_at IS NOT NULL AND backfill_verified_at IS NULL "
                + "FROM region_datasets").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM pg_namespace WHERE nspname = 'sgis_import'")
                .query(Long.class).single()).isZero();

        var points = jdbc.sql("SELECT code, ST_X(display_point) AS longitude, ST_Y(display_point) AS latitude "
                + "FROM regions WHERE level = 'EMD' ORDER BY code LIMIT 5")
                .query((row, index) -> SamplePoint.of(row.getString("code"), row.getDouble("longitude"),
                        row.getDouble("latitude"))).list();
        var factory = new GeometryFactory(new PrecisionModel(), 4326);
        for (var sample : points) {
            var classification = classifier.classify(factory.createPoint(new Coordinate(sample.longitude(), sample.latitude())));
            assertThat(classification.regionCode()).isEqualTo(sample.code());
            assertThat(classification.classifiedAt()).isNotNull();
        }
    }

    private record SamplePoint(String code, double longitude, double latitude) {

        public static SamplePoint of(String code, double longitude, double latitude) {
            return new SamplePoint(code, longitude, latitude);
        }
    }
}
