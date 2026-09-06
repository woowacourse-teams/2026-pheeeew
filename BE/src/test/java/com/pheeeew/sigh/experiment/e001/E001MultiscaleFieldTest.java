package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class E001MultiscaleFieldTest {

    @ParameterizedTest
    @CsvSource({
            "GRADIENT, P1, 953850, 1951950, bfa50023abbeb1e8",
            "GRADIENT, P1, 962850, 1951950, bf9b9cd4739a6f26",
            "GRADIENT, P1, -150, -450, 3f94a1d1e29fd550",
            "GRADIENT, P2, 953850, 1951950, 3fd61a4dfaed7ee4",
            "GRADIENT, P2, 962850, 1951950, 3fdd1412a1b55e3a",
            "GRADIENT, P2, -150, -450, 3fc017ab8429aa09",
            "VALUE, P1, 953850, 1951950, bfaa1007f815f768",
            "VALUE, P1, 962850, 1951950, 3f8cca184bb91e60",
            "VALUE, P1, -150, -450, bfcdaebb8c941238",
            "VALUE, P2, 953850, 1951950, 3f8101c8e0f191f8",
            "VALUE, P2, 962850, 1951950, bfbf5fc4fd93de88",
            "VALUE, P2, -150, -450, 3fc57f1fdab6fe8c"
    })
    void 회전과_스케일과_가중합은_절대_좌표에서_raw_bit를_재현한다(
            E001Noise noise, E001FieldProfile profile, double x, double y, String expectedHex
    ) {
        // given
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);

        // when
        double value = field.valueAt(x, y);

        // then
        assertThat(Double.doubleToRawLongBits(value)).isEqualTo(Long.parseUnsignedLong(expectedHex, 16));
    }

    @ParameterizedTest
    @CsvSource({"GRADIENT, P1", "GRADIENT, P2", "VALUE, P1", "VALUE, P2"})
    void 인접_300미터_격자_경계에서도_field가_연속이다(E001Noise noise, E001FieldProfile profile) {
        // given
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);
        double epsilon = 1e-5;

        // when
        double left = field.valueAt(954_000.0 - epsilon, 1_951_950.0);
        double right = field.valueAt(954_000.0 + epsilon, 1_951_950.0);
        double below = field.valueAt(953_850.0, 1_952_100.0 - epsilon);
        double above = field.valueAt(953_850.0, 1_952_100.0 + epsilon);

        // then
        assertThat(StrictMath.abs(left - right)).isLessThan(1e-5);
        assertThat(StrictMath.abs(below - above)).isLessThan(1e-5);
    }

    @ParameterizedTest
    @CsvSource({"GRADIENT, P1", "GRADIENT, P2", "VALUE, P1", "VALUE, P2"})
    void field는_호출_순서와_무관하며_고정_표본에서_유한한_범위를_지킨다(
            E001Noise noise, E001FieldProfile profile
    ) {
        // given
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);
        double first = field.valueAt(953_850.0, 1_951_950.0);

        // when
        double[] values = new double[121];
        for (int index = 0; index < values.length; index++) {
            values[index] = field.valueAt(953_850.0 + (index % 11 - 5) * 137.0,
                    1_951_950.0 + (index / 11 - 5) * 193.0);
        }
        double repeated = field.valueAt(953_850.0, 1_951_950.0);

        // then
        assertThat(Double.doubleToRawLongBits(repeated)).isEqualTo(Double.doubleToRawLongBits(first));
        for (double value : values) {
            assertThat(value).isFinite().isBetween(-1.0, 1.0);
        }
    }
}
