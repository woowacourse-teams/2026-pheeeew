package com.pheeeew.region;

import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.support.PostgisDataJpaTest;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
class RegionMigrationIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void 지역_계층과_WGS84_경계를_저장하고_초기_검증_상태는_미완료로_둔다() {
        // given
        assertThat(jdbc.sql("SELECT COUNT(*) FROM regions").query(Long.class).single()).isZero();

        // when
        검증용_지역_계층을_저장한다(jdbc);

        // then
        assertThat(jdbc.sql("""
                SELECT code || ':' || level || ':' || COALESCE(parent_code, '-')
                FROM regions ORDER BY code
                """).query(String.class).list())
                .containsExactly("11:SIDO:-", "11010:SIGUNGU:11", "11010530:EMD:11010");
        assertThat(jdbc.sql("""
                SELECT ST_SRID(boundary) = 4326 AND ST_SRID(display_point) = 4326
                FROM regions WHERE code = '11010530'
                """).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("""
                SELECT boundaries_verified_at IS NULL AND backfill_verified_at IS NULL
                FROM region_datasets WHERE dataset_key = 'SGIS_2025_2Q'
                """).query(Boolean.class).single()).isTrue();
        assertThat(regionNullability(jdbc))
                .containsExactly("region_classified_at:NO", "region_code:NO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL, CURRENT_TIMESTAMP", "'11010530', NULL"})
    void 지역_코드나_분류_시각이_NULL인_감정_INSERT를_거부한다(String regionValues) {
        // given
        검증용_지역_계층을_저장한다(jdbc);

        // when / then: Hibernate 검증을 우회해도 DB의 NOT NULL 제약이 적용된다.
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO emotions (request_id, location, nickname, created_at, updated_at,
                                      region_code, region_classified_at)
                VALUES (gen_random_uuid(), ST_SetSRID(ST_MakePoint(127, 38), 4326),
                        '검증용 감정', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                """ + regionValues + ")").update())
                .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                        error -> assertThat(((SQLException) error.getRootCause()).getSQLState()).isEqualTo("23502"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 기존_NULL이_남으면_마이그레이션을_롤백하고_정리_후_재실행한다(boolean classified) {
        // given: 전환 검증에만 임시 DB를 사용한다. 일반 테스트 DB는 최신 스키마를 유지한다.
        String database = "region_migration_" + UUID.randomUUID().toString().replace("-", "");
        var container = postgis();
        String url = container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database);
        var dataSource = new DriverManagerDataSource(url, container.getUsername(), container.getPassword());
        jdbc.sql("CREATE DATABASE " + database).update();
        try {
            Flyway.configure().dataSource(dataSource).target("20261005.1").load().migrate();
            JdbcClient before = JdbcClient.create(dataSource);
            before.sql("""
                    INSERT INTO emotions (request_id, location, nickname, created_at, updated_at,
                                          region_code, region_classified_at)
                    VALUES (gen_random_uuid(), ST_SetSRID(ST_MakePoint(0, 0), 4326),
                            '기존 미매칭', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
                    """ + (classified ? "CURRENT_TIMESTAMP" : "NULL") + ")").update();
            Flyway latest = Flyway.configure().dataSource(dataSource).load();

            // when / then: 실패 후에도 두 컬럼과 기존 기록이 보존된다.
            assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class);
            assertThat(regionNullability(before))
                    .containsExactly("region_classified_at:YES", "region_code:YES");
            assertThat(before.sql("SELECT count(*) FROM emotions").query(Long.class).single()).isOne();

            // 승인된 잔여 기록 정리를 테스트 DB에서만 재현한다. repair 없이 재실행할 수 있다.
            before.sql("DELETE FROM emotions").update();
            latest.migrate();
            assertThat(regionNullability(before))
                    .containsExactly("region_classified_at:NO", "region_code:NO");
        } finally {
            jdbc.sql("DROP DATABASE " + database).update();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "code = '1'", "code = 'abcdefgh'", "level = 'UNKNOWN'", "name = '   '",
            "parent_code = '11'",
            "code = '11020530', parent_code = '11020'",
            "boundary = ST_GeomFromText('MULTIPOLYGON EMPTY', 4326)",
            "boundary = ST_GeomFromText('MULTIPOLYGON(((126 37,128 39,126 39,128 37,126 37)))',4326)",
            "boundary = ST_Translate(boundary, 360, 0), display_point = ST_Translate(display_point, 360, 0)",
            "display_point = ST_GeomFromText('POINT EMPTY', 4326)",
            "display_point = ST_GeomFromText('POINT(129 38)', 4326)"
    })
    void 코드_계층과_공간_계약을_위반한_지역은_거부한다(String assignment) {
        // given
        검증용_지역_계층을_저장한다(jdbc);

        // when / then
        assertThatThrownBy(() -> jdbc.sql("UPDATE regions SET " + assignment
                + " WHERE code = '11010530'").update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "11 | parent_code = '11010'",
            "11010 | parent_code = NULL",
            "11010 | parent_code = '11010'"
    })
    void 시도와_시군구도_부모_계층을_지켜야_한다(String code, String assignment) {
        // given
        검증용_지역_계층을_저장한다(jdbc);

        // when / then
        assertThatThrownBy(() -> jdbc.sql("UPDATE regions SET " + assignment
                + " WHERE code = :code").param("code", code).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "boundaries_verified_at = CURRENT_TIMESTAMP",
            "boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP"
    })
    void 경계_검증과_이후_백필_검증_상태를_기록할_수_있다(String assignment) {
        // given: 경계와 백필 모두 미검증인 데이터셋을 사용한다.

        // when
        int updated = jdbc.sql("UPDATE region_datasets SET " + assignment).update();

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT boundaries_verified_at IS NOT NULL
                  AND (backfill_verified_at IS NULL OR backfill_verified_at >= boundaries_verified_at)
                FROM region_datasets WHERE dataset_key = 'SGIS_2025_2Q'
                """).query(Boolean.class).single()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dataset_key = 'SGIS_2026_2Q'", "backfill_verified_at = CURRENT_TIMESTAMP",
            "boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP - INTERVAL '1 second'"
    })
    void 다른_데이터셋과_경계_검증_전_백필_완료는_거부한다(String assignment) {
        // given: 마이그레이션이 만든 미검증 데이터셋을 사용한다.

        // when / then
        assertThatThrownBy(() -> jdbc.sql("UPDATE region_datasets SET " + assignment).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> regionNullability(JdbcClient client) {
        return client.sql("""
                SELECT column_name || ':' || is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'emotions'
                  AND column_name IN ('region_code', 'region_classified_at')
                ORDER BY column_name
                """).query(String.class).list();
    }
}
