package com.pheeeew.sigh.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.sigh.application.SighLocationGenerator;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.security.SecureRandom;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
class PostgisSighLocationGeneratorIntegrationTest {

    private static final int WGS84_SRID = 4326;
    private static final double RADIUS_METERS = 300.0;
    private static final double TRANSFORM_TOLERANCE_METERS = 0.0001;
    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;

    @Autowired
    private SighLocationGenerator sighLocationGenerator;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SighRepository sighRepository;

    @ParameterizedTest
    @CsvSource({"126.9780, 37.5664", "129.0756, 35.1796", "126.5312, 33.4996"})
    void 근사_좌표에서_투영_거리_300미터_이내의_WGS84_위치를_생성한다(
            double centerLongitude, double centerLatitude
    ) {
        // given / when
        Point location = sighLocationGenerator.generate(centerLongitude, centerLatitude);

        // then
        ProjectedOffset offset = findProjectedOffset(location, centerLongitude, centerLatitude);
        assertThat(location.getSRID()).isEqualTo(WGS84_SRID);
        assertThat(location.getX()).isBetween(-180.0, 180.0);
        assertThat(location.getY()).isBetween(-90.0, 90.0);
        assertThat(Math.hypot(offset.easting(), offset.northing()))
                .isLessThanOrEqualTo(RADIUS_METERS + TRANSFORM_TOLERANCE_METERS);
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0, 0", "0.25, 0.25, 0, 150", "0.81, 0, 270, 0", "0.81, 0.5, -270, 0"})
    void 생성기가_원_오프셋을_격자_재정렬_없이_PostGIS에_적용한다(
            double radialUniform, double angularUniform, double expectedEasting, double expectedNorthing
    ) {
        // given
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextDouble()).thenReturn(radialUniform, angularUniform);
        SighLocationGenerator generator = new PostgisSighLocationGenerator(sighRepository, random);

        // when
        Point location = generator.generate(SEOUL_CITY_HALL_LONGITUDE, SEOUL_CITY_HALL_LATITUDE);

        // then
        ProjectedOffset offset = findProjectedOffset(location, SEOUL_CITY_HALL_LONGITUDE, SEOUL_CITY_HALL_LATITUDE);
        assertThat(location.getSRID()).isEqualTo(WGS84_SRID);
        assertThat(offset.easting()).isCloseTo(expectedEasting, within(TRANSFORM_TOLERANCE_METERS));
        assertThat(offset.northing()).isCloseTo(expectedNorthing, within(TRANSFORM_TOLERANCE_METERS));
    }

    private ProjectedOffset findProjectedOffset(Point location, double centerLongitude, double centerLatitude) {
        return jdbcClient.sql("""
                        WITH projected_points AS (
                            SELECT
                                ST_Transform(
                                    ST_SetSRID(
                                        ST_MakePoint(:locationLongitude, :locationLatitude),
                                        4326
                                    ),
                                    5179
                                ) AS display_location,
                                ST_Transform(
                                    ST_SetSRID(
                                        ST_MakePoint(:centerLongitude, :centerLatitude),
                                        4326
                                    ),
                                    5179
                                ) AS center
                        )
                        SELECT
                            ST_X(display_location) - ST_X(center) AS easting,
                            ST_Y(display_location) - ST_Y(center) AS northing
                        FROM projected_points
                        """)
                .param("locationLongitude", location.getX())
                .param("locationLatitude", location.getY())
                .param("centerLongitude", centerLongitude)
                .param("centerLatitude", centerLatitude)
                .query((resultSet, rowNumber) -> ProjectedOffset.of(
                        resultSet.getDouble("easting"),
                        resultSet.getDouble("northing")
                ))
                .single();
    }

    private record ProjectedOffset(double easting, double northing) {

        private static ProjectedOffset of(double easting, double northing) {
            return new ProjectedOffset(easting, northing);
        }
    }
}
