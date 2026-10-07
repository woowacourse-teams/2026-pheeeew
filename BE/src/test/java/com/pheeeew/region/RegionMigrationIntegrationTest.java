package com.pheeeew.region;

import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.support.PostgisDataJpaTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

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
}
