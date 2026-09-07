package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class E007ClientLocationExperimentTest {

    @Test
    void 격자_경계와_음수도_같은_300미터_규칙을_따른다() {
        // given
        double[] inputs = {-450, -150.001, -150, 0, 149.999, 150, 450};
        double[] expected = {-300, -300, 0, 0, 0, 300, 600};
        // when / then
        for (int i = 0; i < inputs.length; i++) {
            assertThat(E007ClientLocationExperiment.snap(inputs[i])).isEqualTo(expected[i]);
        }
    }

    @Test
    void 기준_D는_실제_위치를_격자화한_뒤_기존_sampler를_그대로_쓴다() {
        // given
        var truth = E007ClientLocationExperiment.XY.of(410, -180);
        var rng = new SplittableRandom(42);
        var expected = (E001SamplingResult.Success) E001DistanceSampler.sampleTaperedGaussian(rng::nextDouble, 120);
        // when
        var actual = E007ClientLocationExperiment.display(E007ClientLocationExperiment.Model.D, truth, new SplittableRandom(42));
        // then
        assertThat(actual.x()).isEqualTo(300 + expected.offset().eastingMeters());
        assertThat(actual.y()).isEqualTo(-300 + expected.offset().northingMeters());
    }

    @Test
    void 거리_보정_적분은_수렴하고_잘못된_구간은_거절한다() {
        // given / when
        double coarse = E007ClientLocationExperiment.kernelMoment(3000);
        double fine = E007ClientLocationExperiment.kernelMoment(30000);
        // then
        assertThat(coarse).isCloseTo(fine, within(1e-6));
        assertThatThrownBy(() -> E007ClientLocationExperiment.kernelMoment(3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(E007ClientLocationExperiment.TARGET_M2).isEqualTo(fine + 15000);
    }

    @Test
    void 연속_모델은_계산한_2차모멘트와_방향_대칭을_따른다() {
        // given
        var origin = E007ClientLocationExperiment.XY.of(0, 0);
        int count = 200000;
        for (var model : new E007ClientLocationExperiment.Model[]{E007ClientLocationExperiment.Model.G, E007ClientLocationExperiment.Model.L}) {
            var random = new SplittableRandom(73192);
            double m2 = 0, x = 0, y = 0, c4 = 0, s4 = 0;
            // when
            for (int i = 0; i < count; i++) {
                var p = E007ClientLocationExperiment.display(model, origin, random);
                m2 += p.radius() * p.radius(); x += p.x(); y += p.y();
                double angle = 4 * StrictMath.atan2(p.y(), p.x());
                c4 += StrictMath.cos(angle); s4 += StrictMath.sin(angle);
            }
            // then
            assertThat(m2 / count / E007ClientLocationExperiment.TARGET_M2).isCloseTo(1, within(.02));
            assertThat(StrictMath.hypot(x, y) / count).isLessThan(2);
            assertThat(StrictMath.hypot(c4, s4) / count).isLessThan(.01);
        }
    }

    @Test
    void 모델별_seed가_분리되고_동일_seed의_좌표는_재현된다() {
        // given
        var seeds = new HashSet<Long>();
        var truth = E007ClientLocationExperiment.XY.of(120, -90);
        // when / then
        for (var model : E007ClientLocationExperiment.Model.values()) {
            for (int trajectory = 0; trajectory < 500; trajectory++) {
                long seed = E007ClientLocationExperiment.seed("test|" + model + "|" + trajectory);
                seeds.add(seed);
                assertThat(E007ClientLocationExperiment.display(model, truth, new SplittableRandom(seed)))
                        .isEqualTo(E007ClientLocationExperiment.display(model, truth, new SplittableRandom(seed)));
            }
        }
        assertThat(seeds).hasSize(1500);
    }
}
