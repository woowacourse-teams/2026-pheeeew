package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class E006VirtualCenterExperimentTest {

    @Test
    void 서로_다른_표본과_seed의_난수열이_겹치지_않는다() {
        // given
        var identities = new HashSet<Long>();
        // when
        for (long seed = 2026090701L; seed <= 2026090705L; seed++) {
            for (int index = 0; index < 1000; index++) {
                identities.add(E006VirtualCenterExperiment.pointSeed(0, 5000, seed, 0, index));
            }
        }
        // then
        assertThat(identities).hasSize(5000);
    }

    @Test
    void 기준_D는_기존_sampler와_같은_좌표를_반환한다() {
        // given
        SplittableRandom random = new SplittableRandom(42);
        E001SamplingResult.Success expected = (E001SamplingResult.Success)
                E001DistanceSampler.sampleTaperedGaussian(random::nextDouble, 120);
        // when
        var actual = E006VirtualCenterExperiment.sample(E006VirtualCenterExperiment.Model.D, 42);
        // then
        assertThat(actual.offset()).isEqualTo(expected.offset());
    }

    @Test
    void 가상_중심을_더하면_원래_반경_300미터를_벗어날_수_있다() {
        // given
        E001Offset kernel = E001Offset.of(200, 0, 200);
        // when
        var actual = E006VirtualCenterExperiment.combine(kernel, 149, 0);
        // then
        assertThat(actual).isEqualTo(E001Offset.of(349, 0, 349));
    }

    @Test
    void 보정은_경계로_밀지_않고_합산_후보를_다시_뽑는다() {
        // given
        var proposals = new ArrayDeque<>(List.of(E001Offset.of(300, 0, 300), E001Offset.of(0, 0, 0)));
        // when
        var actual = E006VirtualCenterExperiment.constrain(proposals::removeFirst, () -> 0.0, 2);
        // then
        assertThat(actual).isEqualTo(E006VirtualCenterExperiment.Draw.of(E001Offset.of(0, 0, 0), 2));
    }

    @Test
    void 실패와_잘못된_좌표를_숨기지_않는다() {
        // given / when / then
        assertThatThrownBy(() -> E006VirtualCenterExperiment.constrain(
                () -> E001Offset.of(300, 0, 300), () -> 0.0, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> E006VirtualCenterExperiment.constrain(
                () -> E001Offset.of(Double.NaN, 0, Double.NaN), () -> 0.0, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 동일_seed는_동일_좌표이며_각_모델의_반경을_지킨다() {
        // given
        for (var model : E006VirtualCenterExperiment.Model.values()) {
            double limit = model == E006VirtualCenterExperiment.Model.V ? 300 + 150 * StrictMath.sqrt(2) : 300;
            for (long seed = 0; seed < 1000; seed++) {
                // when
                var actual = E006VirtualCenterExperiment.sample(model, seed);
                // then
                assertThat(actual).isEqualTo(E006VirtualCenterExperiment.sample(model, seed));
                assertThat(actual.offset().radiusMeters()).isBetween(0.0, Math.nextDown(limit));
            }
        }
    }
}
