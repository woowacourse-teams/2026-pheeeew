package com.pheeeew.region.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.domain.repository.RegionRepository;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics;
import com.pheeeew.region.infra.metrics.RegionQueryMetricsAspect;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({RegionClassifier.class, RegionRepository.class, AopAutoConfiguration.class,
        RegionQueryMetrics.class, RegionQueryMetricsAspect.class})
class RegionClassifierIntegrationTest {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private RegionClassifier classifier;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
    }

    @Test
    void 상위_지역도_덮는_좌표를_행정동으로_분류한다() {
        // given
        경계_검증을_완료한다();
        long boundaryCount = 지역_조회_수("boundaries_verified", "success");
        long classificationCount = 지역_조회_수("emd_code", "success");

        // when
        var result = classifier.classify(point(127, 38));

        // then
        assertThat(result.regionCode()).isEqualTo("11010530");
        assertThat(result.classifiedAt()).isNotNull();
        assertThat(지역_조회_수("boundaries_verified", "success")).isEqualTo(boundaryCount + 1);
        assertThat(지역_조회_수("emd_code", "success")).isEqualTo(classificationCount + 1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {37, 38})
    void 공유_경계선과_꼭짓점은_코드_순서로_하나의_행정동을_선택한다(double latitude) {
        // given: 면적은 겹치지 않고 경계만 공유하는 합성 행정동을 나중에 추가한다.
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                VALUES ('11010529', 'EMD', '인접 검증 지역', '11010',
                        ST_GeomFromText('MULTIPOLYGON(((128 37,129 37,129 39,128 39,128 37)))', 4326),
                        ST_GeomFromText('POINT(128.5 38)', 4326))
                """).update();
        경계_검증을_완료한다();

        // when / then
        assertThat(classifier.classify(point(128, latitude)).regionCode()).isEqualTo("11010529");
    }

    @ParameterizedTest
    @CsvSource({"129, 38", "0, 0"})
    void 검증된_경계_밖의_좌표도_정상_미매칭으로_분류를_완료한다(double longitude, double latitude) {
        // given
        경계_검증을_완료한다();

        // when
        var result = classifier.classify(point(longitude, latitude));

        // then
        assertThat(result.regionCode()).isNull();
        assertThat(result.classifiedAt()).isNotNull();
    }

    @Test
    void 면적_겹침_안의_좌표도_코드_순서로_하나의_행정동에만_분류한다() {
        // given: 코드가 더 작은 행정동을 나중에 삽입한다.
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                VALUES ('11010529', 'EMD', '겹치는 합성 지역', '11010',
                        ST_GeomFromText('MULTIPOLYGON(((127 37,129 37,129 39,127 39,127 37)))', 4326),
                        ST_GeomFromText('POINT(128 38)', 4326))
                """).update();
        경계_검증을_완료한다();
        assertThat(jdbc.sql("SELECT count(*) FROM regions WHERE level = 'EMD' "
                + "AND ST_Covers(boundary, ST_GeomFromText('POINT(127.5 38)', 4326))")
                .query(Long.class).single()).isEqualTo(2);

        // when
        var result = classifier.classify(point(127.5, 38));

        // then
        assertThat(result.regionCode()).isEqualTo("11010529");
        assertThat(result.classifiedAt()).isNotNull();
    }

    @Test
    void 포함된_행정동이_있으면_인접한_더_작은_코드보다_우선한다() {
        // given
        인접_행정동을_추가한다("11010529", 2.001);
        경계_검증을_완료한다();

        // when / then
        assertThat(classifier.classify(point(128, 38)).regionCode()).isEqualTo("11010530");
    }

    @ParameterizedTest
    @CsvSource({"999.9,11010530", "1000,11010530", "1000.1,"})
    void 경계에서_1km_이하만_배정하고_초과하면_미매칭을_기록한다(double meters, String expectedCode) {
        // given: 동쪽 경계의 점에서 타원체 기준 동쪽으로 이동한 합성 좌표다.
        경계_검증을_완료한다();
        Point location = jdbc.sql("""
                SELECT ST_X(p) AS longitude, ST_Y(p) AS latitude FROM (
                    SELECT ST_Project(ST_SetSRID(ST_MakePoint(128, 38), 4326)::geography,
                        :meters, radians(90))::geometry AS p
                ) projected
                """).param("meters", meters)
                .query((row, index) -> point(row.getDouble("longitude"), row.getDouble("latitude"))).single();

        // when
        var result = classifier.classify(location);

        // then
        assertThat(result.regionCode()).isEqualTo(expectedCode);
        assertThat(result.classifiedAt()).isNotNull();
        assertThat(location.getSRID()).isEqualTo(4326);
    }

    @Test
    void 표시점과_코드_순서보다_폴리곤까지의_최단_거리를_우선한다() {
        // given: 두 지역 모두 1km 이내지만 더 작은 코드의 표시점만 더 가깝다.
        인접_행정동을_추가한다("11010529", 2.017);
        jdbc.sql("UPDATE regions SET display_point = ST_GeomFromText('POINT(128.017 38)', 4326) "
                + "WHERE code = '11010529'").update();
        경계_검증을_완료한다();

        // when / then
        assertThat(classifier.classify(point(128.007, 38)).regionCode()).isEqualTo("11010530");
    }

    @Test
    void 경계_밖에서_거리가_같으면_삽입_순서와_관계없이_코드_순서로_선택한다() {
        // given: 동일한 합성 도형으로 정확히 같은 거리를 만든다.
        인접_행정동을_추가한다("11010529", 0);
        경계_검증을_완료한다();

        // when / then
        assertThat(classifier.classify(point(128.005, 38)).regionCode()).isEqualTo("11010529");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 미검증과_누락_데이터셋은_정상_미매칭으로_처리하지_않는다(boolean missing) {
        // given
        if (missing) {
            jdbc.sql("DELETE FROM region_datasets").update();
        }

        // when / then
        assertThatThrownBy(() -> classifier.classify(point(127, 38)))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {127, 128.005})
    void 분류_SQL이_실패하면_정상_미매칭으로_처리하지_않는다(double longitude) {
        // given: 트랜잭션 종료 시 롤백될 DDL로 조회 장애를 만든다.
        경계_검증을_완료한다();
        jdbc.sql("ALTER TABLE regions RENAME TO unavailable_regions").update();
        long errorCount = 지역_조회_수("emd_code", "error");

        // when / then
        assertThatThrownBy(() -> classifier.classify(point(longitude, 38))).isInstanceOf(DataAccessException.class);
        assertThat(지역_조회_수("emd_code", "error")).isEqualTo(errorCount + 1);
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 요청한_계층만_선택하고_코드와_부모_및_고정_표시점을_반환한다(RegionLevel level) {
        // given
        경계_검증을_완료한다();
        long successCount = 공간_조회_수("success");
        String code = switch (level) {
            case SIDO -> "11";
            case SIGUNGU -> "11010";
            case EMD -> "11010530";
        };
        // when / then: 감정이 없어도 교차하는 지역 조회는 가능하다.
        assertThat(classifier.findIntersectingRegions(EmotionSearchBounds.of(126.5, 37.5, 127.5, 38.5), level))
                .singleElement().satisfies(row -> {
                    assertThat(row.code()).isEqualTo(code);
                    assertThat(row.level()).isEqualTo(level);
                    assertThat(row.name()).isEqualTo("검증용 " + level);
                    assertThat(row.parentCode()).isEqualTo(level == RegionLevel.SIDO ? null : code.substring(0, code.length() == 5 ? 2 : 5));
                    assertThat(row.longitude()).isEqualTo(127);
                    assertThat(row.latitude()).isEqualTo(38);
                });
        assertThat(공간_조회_수("success")).isEqualTo(successCount + 1);
    }

    @Test
    void 경계_일부만_걸치면_화면_밖의_고정_표시점도_반환한다() {
        // given / when: 동쪽 일부만 보이는 화면에는 표시점 (127, 38)이 없다.
        경계_검증을_완료한다();
        var found = classifier.findIntersectingRegions(EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8), RegionLevel.EMD);
        // then
        assertThat(found).singleElement().satisfies(row -> {
            assertThat(row.code()).isEqualTo("11010530");
            assertThat(row.longitude()).isEqualTo(127);
            assertThat(row.latitude()).isEqualTo(38);
        });
    }

    @ParameterizedTest
    @CsvSource({"128,38,true", "128,39,true", "128.0001,39.0001,false"})
    void 경계선과_꼭짓점_접촉은_포함하고_실제로_떨어진_화면은_제외한다(double west, double south, boolean intersects) {
        // given / when
        경계_검증을_완료한다();
        var found = classifier.findIntersectingRegions(EmotionSearchBounds.of(west, south, 129, 40), RegionLevel.EMD);
        // then
        assertThat(found).hasSize(intersects ? 1 : 0);
    }

    @Test
    void 도형의_bbox만_겹치고_실제_경계와_교차하지_않는_화면은_제외한다() {
        // given: 가운데 구멍이 있는 합성 도형의 구멍 안을 조회한다.
        경계_검증을_완료한다();
        jdbc.sql("""
                UPDATE regions SET boundary = ST_GeomFromText(
                    'MULTIPOLYGON(((126 37,128 37,128 39,126 39,126 37),
                    (126.5 37.5,126.5 38.5,127.5 38.5,127.5 37.5,126.5 37.5)))', 4326),
                    display_point = ST_GeomFromText('POINT(127 37.25)', 4326) WHERE code = '11010530'
                """).update();
        // when / then
        assertThat(classifier.findIntersectingRegions(EmotionSearchBounds.of(126.9, 37.9, 127.1, 38.1), RegionLevel.EMD)).isEmpty();
    }

    @Test
    void 날짜변경선_양쪽에_걸친_한_지역도_한_번만_반환한다() {
        // given: 한 MultiPolygon의 두 조각이 양쪽 bbox와 각각 교차한다.
        경계_검증을_완료한다();
        jdbc.sql("""
                UPDATE regions SET boundary = ST_GeomFromText(
                    'MULTIPOLYGON(((-180 37,-179 37,-179 39,-180 39,-180 37)),
                    ((179 37,180 37,180 39,179 39,179 37)))', 4326),
                    display_point = ST_GeomFromText('POINT(179.5 38)', 4326) WHERE code = '11010530'
                """).update();
        // when / then
        assertThat(classifier.findIntersectingRegions(EmotionSearchBounds.of(179.25, 37.5, -179.25, 38.5), RegionLevel.EMD))
                .extracting(row -> row.code()).containsExactly("11010530");
    }

    @Test
    void 삽입_순서와_관계없이_지역_코드로_정렬한다() {
        // given
        경계_검증을_완료한다();
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT '11010529', level, name, parent_code, boundary, display_point FROM regions WHERE code = '11010530'
                """).update();
        // when / then
        assertThat(classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .extracting(row -> row.code()).containsExactly("11010529", "11010530");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 미검증이나_누락_데이터셋을_빈_지역_목록으로_숨기지_않는다(boolean missing) {
        // given
        경계_검증을_완료한다();
        jdbc.sql(missing ? "DELETE FROM region_datasets" : "UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        long successCount = 공간_조회_수("success");
        long errorCount = 공간_조회_수("error");
        long boundaryCount = 지역_조회_수("boundaries_verified", "success");
        // when / then
        assertThatThrownBy(() -> classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
        assertThat(공간_조회_수("success")).isEqualTo(successCount);
        assertThat(공간_조회_수("error")).isEqualTo(errorCount);
        assertThat(지역_조회_수("boundaries_verified", "success")).isEqualTo(boundaryCount + 1);
    }

    @Test
    void 경계_검증_조회_SQL_장애를_실패로_기록하고_예외를_유지한다() {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        jdbc.sql("ALTER TABLE region_datasets RENAME TO unavailable_region_datasets").update();
        long errorCount = 지역_조회_수("boundaries_verified", "error");

        // when / then
        assertThatThrownBy(() -> classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .isInstanceOf(DataAccessException.class);
        assertThat(지역_조회_수("boundaries_verified", "error")).isEqualTo(errorCount + 1);
    }

    @Test
    void 지역_조회_SQL_장애를_빈_결과로_숨기지_않는다() {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        경계_검증을_완료한다();
        jdbc.sql("ALTER TABLE regions RENAME TO unavailable_regions").update();
        long errorCount = 공간_조회_수("error");
        // when / then
        assertThatThrownBy(() -> classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .isInstanceOf(DataAccessException.class);
        assertThat(공간_조회_수("error")).isEqualTo(errorCount + 1);
    }

    private long 공간_조회_수(String outcome) {
        return 지역_조회_수("intersecting_regions", outcome);
    }

    private long 지역_조회_수(String operation, String outcome) {
        return meterRegistry.get("pheeeew.region.query")
                .tags("operation", operation, "outcome", outcome).timer().count();
    }

    private void 경계_검증을_완료한다() {
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
    }

    private void 인접_행정동을_추가한다(String code, double longitudeShift) {
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT :code, level, name, parent_code,
                    ST_Translate(boundary, :shift, 0), ST_Translate(display_point, :shift, 0)
                FROM regions WHERE code = '11010530'
                """).param("code", code).param("shift", longitudeShift).update();
    }

    private Point point(double longitude, double latitude) {
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }
}
