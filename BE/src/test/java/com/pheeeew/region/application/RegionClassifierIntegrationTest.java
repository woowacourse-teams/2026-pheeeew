package com.pheeeew.region.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.region.domain.repository.RegionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private void 경계_검증을_완료한다() {
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
    }

    private Point point(double longitude, double latitude) {
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }
}
