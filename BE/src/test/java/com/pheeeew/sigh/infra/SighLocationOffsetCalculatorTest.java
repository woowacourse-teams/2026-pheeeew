package com.pheeeew.sigh.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SighLocationOffsetCalculatorTest {

    @ParameterizedTest
    @CsvSource({"0, 0, 0, 0", "0.25, 0, 150, 0", "0.25, 0.25, 0, 150",
            "0.25, 0.5, -150, 0", "0.25, 0.75, 0, -150"})
    void 반경은_난수의_제곱근이고_방향은_한_바퀴에_균등하다(
            double radialUniform, double angularUniform, double easting, double northing
    ) {
        // given / when
        var offset = SighLocationOffsetCalculator.calculate(radialUniform, angularUniform);

        // then
        assertThat(offset.eastingMeters()).isCloseTo(easting, within(1e-9));
        assertThat(offset.northingMeters()).isCloseTo(northing, within(1e-9));
    }

    @Test
    void 최대_난수도_반경_300미터를_수치_오차_이내에서_지킨다() {
        // given
        double uniform = Math.nextDown(1.0);

        // when
        var offset = SighLocationOffsetCalculator.calculate(uniform, uniform);

        // then
        assertThat(Math.hypot(offset.eastingMeters(), offset.northingMeters()))
                .isCloseTo(300.0, within(1e-9))
                .isLessThanOrEqualTo(300.0 + 1e-9);
    }

    @Test
    void 균등한_입력은_동일_면적_고리와_사분면에_같은_수의_점을_만든다() {
        // given
        int[] rings = new int[10];
        int[] quadrants = new int[4];

        // when
        for (int radial = 0; radial < 100; radial++) {
            for (int angular = 0; angular < 100; angular++) {
                var offset = SighLocationOffsetCalculator.calculate((radial + 0.5) / 100, (angular + 0.5) / 100);
                double x = offset.eastingMeters();
                double y = offset.northingMeters();
                rings[(int) ((x * x + y * y) / 9000)]++;
                quadrants[(x < 0 ? 2 : 0) + (y < 0 ? 1 : 0)]++;
            }
        }

        // then
        assertThat(rings).containsOnly(1000);
        assertThat(quadrants).containsOnly(2500);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 영_이상_일_미만이_아닌_난수는_거절한다(double invalid) {
        // given / when / then
        assertThatThrownBy(() -> SighLocationOffsetCalculator.calculate(invalid, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SighLocationOffsetCalculator.calculate(0.5, invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
