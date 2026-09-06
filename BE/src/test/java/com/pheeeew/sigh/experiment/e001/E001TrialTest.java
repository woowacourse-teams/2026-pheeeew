package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class E001TrialTest {

    @ParameterizedTest
    @ValueSource(strings = {"A", "B", "C", "D", "sir16", "sir32", "rejection128"})
    void 생성기는_각_점의_seed와_절대_중심을_sampler에_연결하고_재현한다(String model) {
        // given
        E001Parameters parameters = model.length() == 1
                ? E001Parameters.distance(model, model.equals("C") || model.equals("D") ? 120 : 0)
                : E001Parameters.of(120, E001Noise.GRADIENT, E001FieldProfile.P1, 0.7, model);
        E001Scenario.Plan plan = E001Scenario.Plan.of(E001Scenario.TUNING_GRID,
                List.of(E001Scenario.Center.of("r02c02", 1, -1, 2), E001Scenario.Center.of("r00c00", -1, 1, 2)));

        // when
        E001Batch batch = E001Batch.generate(parameters, plan);

        // then
        assertThat(batch.samples()).hasSize(20).isSortedAccordingTo(E001Sample.ORDER);
        assertThat(batch.failures()).isEmpty();
        assertThat(batch.integrityPassed()).isTrue();
        assertThat(E001Batch.generate(parameters, plan)).isEqualTo(batch);
        E001Sample first = batch.samples().getFirst();
        assertThat(first.centerEasting()).isEqualTo(953_550.0);
        assertThat(first.centerNorthing()).isEqualTo(1_952_250.0);
        long seed = E001PointSeed.derive("tuning-grid-imbalanced-n4500", "CAL", -1, 1, 2_026_090_301L, 0);
        E001SamplingResult.Success expected = (E001SamplingResult.Success) parameters.pointSampler()
                .sample(E001SplitMix64.from(seed), first.centerEasting(), first.centerNorthing());
        assertThat(first.offset()).isEqualTo(expected.offset());
    }

    @Test
    void 실패한_점은_대체하지_않고_다음_점의_seed와_인덱스를_유지한다() {
        // given
        E001Parameters parameters = E001Parameters.distance("D", 120);
        E001Scenario.Plan plan = smallSingle(3);
        AtomicInteger calls = new AtomicInteger();

        // when
        E001Batch batch = E001Batch.generate(parameters, plan, (random, x, y) -> {
            int call = calls.getAndIncrement();
            double radius = random.nextDouble();
            return call % 3 == 1 ? E001SamplingResult.Failure.from(4_096)
                    : E001SamplingResult.Success.of(E001Offset.of(radius, 0.0, radius), 1);
        });
        E001Trial trial = E001Trial.measure(batch, null);

        // then
        assertThat(calls.get()).isEqualTo(15);
        assertThat(batch.samples()).hasSize(10);
        assertThat(batch.samples()).extracting(E001Sample::pointIndex).containsOnly(0L, 2L);
        assertThat(batch.failures()).hasSize(5).allSatisfy(failure -> {
            assertThat(failure.pointIndex()).isEqualTo(1L);
            assertThat(failure.attemptLimit()).isEqualTo(4_096);
        });
        assertThat(trial.evaluation().integrityPassed()).isFalse();
        assertThat(trial.evaluation().pooledValue("samplerFailureCount")).isEqualTo(5.0);
        long seed = E001PointSeed.derive("tuning-single-n500", "CAL", 0, 0, 2_026_090_301L, 2);
        assertThat(batch.samples().get(1).offset().eastingMeters()).isEqualTo(E001SplitMix64.from(seed).nextDouble());
    }

    @Test
    void trial은_seed별_형태와_다섯_seed를_pool한_밀도를_분리한다() {
        // given
        E001Parameters d = E001Parameters.distance("D", 120);
        E001Scenario.Plan plan = smallSingle(2);
        E001Batch batch = E001Batch.generate(d, plan, (random, x, y) ->
                E001SamplingResult.Success.of(E001Offset.of(10.0, 0.0, 10.0), 1));

        // when
        E001Trial trial = E001Trial.measure(batch, null);

        // then
        assertThat(trial.evaluation().integrityPassed()).isTrue();
        assertThat(trial.evaluation().bySeed()).hasSize(5);
        assertThat(trial.evaluation().median("radius.p95")).isEqualTo(10.0);
        assertThat(trial.evaluation().pooledValue("neighbors10")).isEqualTo(9.0);
        assertThat(trial.evaluation().pooledValue("hotspot50.max")).isEqualTo(10.0);
        assertThat(trial.evaluation().pooled().values()).doesNotContainKey("grid300");
    }

    @Test
    void E_trial은_같은_sigma와_같은_seed의_D_표본으로_KS를_계산한다() {
        // given
        E001Scenario.Plan plan = smallSingle(2);
        E001Trial d = E001Trial.run(E001Parameters.distance("D", 120), plan, null);
        E001Parameters eParameters = E001Parameters.of(120, E001Noise.VALUE, E001FieldProfile.P2, 0.4, "sir16");
        E001Batch eBatch = E001Batch.of(eParameters, plan, d.batch().samples(), List.of());

        // when
        E001Trial e = E001Trial.measure(eBatch, d);

        // then
        assertThat(e.evaluation().median("radialKs")).isZero();
        assertThatThrownBy(() -> E001Trial.measure(eBatch, null)).isInstanceOf(IllegalArgumentException.class);
        E001Trial wrongSigma = E001Trial.run(E001Parameters.distance("D", 100), plan, null);
        assertThatThrownBy(() -> E001Trial.measure(eBatch, wrongSigma)).isInstanceOf(IllegalArgumentException.class);
        E001Trial wrongPlan = E001Trial.run(E001Parameters.distance("D", 120), smallSingle(1), null);
        assertThatThrownBy(() -> E001Trial.measure(eBatch, wrongPlan)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spectral_trial은_buffer_표본을_보존하고_중앙_창의_격자_지표를_계산한다() {
        // given
        E001Scenario.Plan plan = E001Scenario.Plan.of(E001Scenario.SPECTRAL_EQUAL, List.of(
                E001Scenario.Center.of("r00c00", -5, 5, 1),
                E001Scenario.Center.of("r05c05", 0, 0, 1),
                E001Scenario.Center.of("r10c10", 5, -5, 1)));
        E001Batch batch = E001Batch.generate(E001Parameters.distance("D", 120), plan,
                (random, x, y) -> E001SamplingResult.Success.of(E001Offset.of(10.0, 30.0, StrictMath.hypot(10.0, 30.0)), 1));

        // when
        E001Trial trial = E001Trial.measure(batch, null);

        // then
        assertThat(trial.batch().samples()).hasSize(15);
        assertThat(trial.evaluation().integrityPassed()).isTrue();
        assertThat(trial.evaluation().pooledValue("hotspot50.max")).isEqualTo(5.0);
        assertThat(trial.evaluation().pooledValue("neighbors10")).isEqualTo(4.0);
        assertThat(trial.evaluation().pooledValue("grid300")).isFinite().isEqualTo(
                E001SpectralMetrics.grid300(batch.samples(), plan.scenario().originX(), plan.scenario().originY()));
    }

    private static E001Scenario.Plan smallSingle(long perSeed) {
        return E001Scenario.Plan.of(E001Scenario.TUNING_SINGLE, List.of(E001Scenario.Center.of("single", 0, 0, perSeed)));
    }
}
