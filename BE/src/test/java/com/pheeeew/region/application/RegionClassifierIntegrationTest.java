package com.pheeeew.region.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.domain.repository.RegionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({RegionClassifier.class, RegionRepository.class})
class RegionClassifierIntegrationTest {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private RegionClassifier classifier;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
    }

    @Test
    void 상위_지역도_덮는_좌표를_행정동으로_분류한다() {
        // given
        경계_검증을_완료한다();

        // when
        var result = classifier.classify(point(127, 38));

        // then
        assertThat(result.regionCode()).isEqualTo("11010530");
        assertThat(result.classifiedAt()).isNotNull();
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

    @Test
    void 분류_SQL이_실패하면_정상_미매칭으로_처리하지_않는다() {
        // given: 트랜잭션 종료 시 롤백될 DDL로 조회 장애를 만든다.
        경계_검증을_완료한다();
        jdbc.sql("ALTER TABLE regions RENAME TO unavailable_regions").update();

        // when / then
        assertThatThrownBy(() -> classifier.classify(point(127, 38))).isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 요청한_계층만_선택하고_코드와_부모_및_고정_표시점을_반환한다(RegionLevel level) {
        // given
        경계_검증을_완료한다();
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
        // when / then
        assertThatThrownBy(() -> classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
    }

    @Test
    void 지역_조회_SQL_장애를_빈_결과로_숨기지_않는다() {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        경계_검증을_완료한다();
        jdbc.sql("ALTER TABLE regions RENAME TO unavailable_regions").update();
        // when / then
        assertThatThrownBy(() -> classifier.findIntersectingRegions(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD))
                .isInstanceOf(DataAccessException.class);
    }

    private void 경계_검증을_완료한다() {
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
    }

    private Point point(double longitude, double latitude) {
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }
}
