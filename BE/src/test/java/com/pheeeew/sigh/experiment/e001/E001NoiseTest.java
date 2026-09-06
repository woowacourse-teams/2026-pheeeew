package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class E001NoiseTest {

    @ParameterizedTest
    @CsvSource({
            "GRADIENT, 0, bc1a98e9e1a861db",
            "GRADIENT, 2, c4c16dcbbabfdd3e",
            "GRADIENT, -1, 19feb20b7cff0b98",
            "VALUE, 0, 43f5165605802903",
            "VALUE, 2, 687a592900c96abb",
            "VALUE, -1, ef8ec95009462472"
    })
    void lattice_hash는_domain과_unsigned_octave와_음수_좌표를_순서대로_반영한다(
            E001Noise noise, int octave, String expectedHex
    ) {
        // given
        long seed = E001MultiscaleField.FIELD_SEED;

        // when
        long hash = noise.latticeHash(seed, octave, -3L, 7L);

        // then
        assertThat(hash).isEqualTo(Long.parseUnsignedLong(expectedHex, 16));
    }

    @ParameterizedTest
    @CsvSource({
            "GRADIENT, 0.125, 0.625, 3fe2e9aa11b7387b",
            "GRADIENT, -1.25, 2.75, bfc66b85c28d0701",
            "GRADIENT, -3.0, -2.0, 0000000000000000",
            "GRADIENT, 1.375, -0.875, bfe442012b832b16",
            "VALUE, 0.125, 0.625, bfe7f79664cf80f2",
            "VALUE, -1.25, 2.75, 3fc8747ea5ef15b8",
            "VALUE, -3.0, -2.0, 3fd3d33715859478",
            "VALUE, 1.375, -0.875, bfd9c207f1e684b1"
    })
    void noise는_고정_좌표에서_raw_bit를_재현한다(E001Noise noise, double x, double y, String expectedHex) {
        // given
        long seed = E001MultiscaleField.FIELD_SEED;

        // when
        double value = noise.valueAt(seed, 2, x, y);

        // then
        assertThat(Double.doubleToRawLongBits(value)).isEqualTo(Long.parseUnsignedLong(expectedHex, 16));
    }

    @ParameterizedTest
    @EnumSource(E001Noise.class)
    void 음수_lattice_경계의_양쪽에서_noise가_연속이다(E001Noise noise) {
        // given
        double epsilon = 1e-7;
        long seed = E001MultiscaleField.FIELD_SEED;

        // when
        double left = noise.valueAt(seed, 0, -3.0 - epsilon, -2.375);
        double right = noise.valueAt(seed, 0, -3.0 + epsilon, -2.375);
        double below = noise.valueAt(seed, 0, -3.375, -2.0 - epsilon);
        double above = noise.valueAt(seed, 0, -3.375, -2.0 + epsilon);

        // then
        assertThat(StrictMath.abs(left - right)).isLessThan(1e-5);
        assertThat(StrictMath.abs(below - above)).isLessThan(1e-5);
    }
}
