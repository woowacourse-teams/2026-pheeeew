package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class E001SplitMix64Test {

    @Test
    void 고정_seed에서_nextLong_수열을_재현한다() {
        // given
        E001SplitMix64 random = E001SplitMix64.from(0L);

        // when
        long[] values = {
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong()
        };

        // then
        assertThat(values).containsExactly(
                0xE220A8397B1DCDAFL,
                0x6E789E6AA1B965F4L,
                0x06C45D188009454FL,
                0xF88BB8A8724C81ECL,
                0x1B39896A51A8749BL
        );
    }

    @Test
    void nextDouble은_상위_53비트로_고정된_double을_만든다() {
        // given
        E001SplitMix64 random = E001SplitMix64.from(0L);

        // when
        long[] rawBits = {
                Double.doubleToRawLongBits(random.nextDouble()),
                Double.doubleToRawLongBits(random.nextDouble()),
                Double.doubleToRawLongBits(random.nextDouble()),
                Double.doubleToRawLongBits(random.nextDouble()),
                Double.doubleToRawLongBits(random.nextDouble())
        };

        // then
        assertThat(rawBits).containsExactly(
                0x3FEC4415072F63B9L,
                0x3FDB9E279AA86E58L,
                0x3F9B117462002500L,
                0x3FEF1177150E4990L,
                0x3FBB39896A51A870L
        );
    }

    @Test
    void mix64는_state_증가_없이_finalizer만_적용한다() {
        // given
        long fieldSeed = 0x5048454545455701L;

        // when
        long mixed = E001SplitMix64.mix64(fieldSeed);

        // then
        assertThat(mixed).isEqualTo(0xF5FC38811A961D99L);
    }
}
