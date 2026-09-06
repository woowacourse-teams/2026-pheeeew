package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class E001PointSeedTest {

    @Test
    void 고정된_입력으로_SHA256의_앞_64비트_seed를_만든다() {
        // given
        String scenarioId = "confirmation-single-n500";
        String originId = "HOLDOUT";

        // when
        long pointSeed = E001PointSeed.derive(scenarioId, originId, 0, 0, 2_026_090_601L, 0L);

        // then
        assertThat(pointSeed).isEqualTo(0x5B180D498DC0BE0BL);
    }

    @Test
    void 음수_격자_좌표도_부호와_선행_0_없이_seed에_반영한다() {
        // given
        String scenarioId = "spectral-grid-equal-n500-per-center";
        String originId = "SPECTRAL";

        // when
        long pointSeed = E001PointSeed.derive(scenarioId, originId, -5, -5, 2_026_090_605L, 0L);

        // then
        assertThat(pointSeed).isEqualTo(0xBE10760A0C594C37L);
    }
}
