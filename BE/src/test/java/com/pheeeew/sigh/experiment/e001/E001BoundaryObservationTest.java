package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class E001BoundaryObservationTest {

    private static final List<Long> SEEDS = E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList();

    @ParameterizedTest
    @CsvSource({"0, 0, empty-bands", "0, 1, inner-observed", "1, 0, outer-only",
            "19, 17, inner-observed", "80, 1, inner-observed"})
    void 사전등록한_빈_띠와_소수_관측과_경계_집중을_보정_없이_구분한다(int outer, int inner, String status) {
        // given
        List<E001Sample> samples = bands(100, outer, inner);

        // when
        E001BoundaryObservation observed = E001BoundaryObservation.of(samples, false);

        // then
        assertThat(observed.generatedCount()).isEqualTo(100);
        assertThat(observed.outerCount()).isEqualTo((long) outer);
        assertThat(observed.innerCount()).isEqualTo((long) inner);
        assertThat(observed.outerShare()).isEqualTo(outer / 100.0);
        assertThat(observed.innerShare()).isEqualTo(inner / 100.0);
        assertThat(observed.status().id()).isEqualTo(status);
        assertThat(observed.interpretation()).isEqualTo("descriptive-only");
        if (inner == 0) {
            assertThat(observed.rawDensityRatio()).isNull();
        } else {
            assertThat(observed.rawDensityRatio()).isCloseTo(outer * 15300.0 / (inner * 17100.0), within(1.0e-12));
        }
    }

    @Test
    void 표본이_없으면_빈_띠와_구분하고_점유율을_0으로_만들지_않는다() {
        // when
        E001BoundaryObservation observed = E001BoundaryObservation.of(List.of(), false);

        // then
        assertThat(observed.status()).isEqualTo(E001BoundaryObservation.Status.NO_SAMPLES);
        assertThat(observed.generatedCount()).isZero();
        assertThat(observed.outerCount()).isZero();
        assertThat(observed.innerCount()).isZero();
        assertThat(observed.outerShare()).isNull();
        assertThat(observed.innerShare()).isNull();
        assertThat(observed.rawDensityRatio()).isNull();
    }

    @ParameterizedTest
    @ValueSource(doubles = {300.0, Double.NaN, Double.POSITIVE_INFINITY})
    void 유효하지_않은_좌표를_제외해_정상_경계_관측처럼_기록하지_않는다(double radius) {
        // given
        List<E001Sample> samples = List.of(point(SEEDS.getFirst(), 0, radius), point(SEEDS.getFirst(), 1, 0));

        // when
        E001BoundaryObservation observed = E001BoundaryObservation.of(samples, false);

        // then
        assertThat(observed.status()).isEqualTo(E001BoundaryObservation.Status.INVALID_SAMPLE);
        assertThat(observed.generatedCount()).isEqualTo(2);
        assertThat(observed.outerCount()).isNull();
        assertThat(observed.innerCount()).isNull();
        assertThat(observed.outerShare()).isNull();
        assertThat(observed.innerShare()).isNull();
        assertThat(observed.rawDensityRatio()).isNull();
    }

    @Test
    void pooled는_seed별_비율을_평균하지_않고_실제_개수를_합산한다() {
        // given
        E001Scenario.Plan plan = E001Scenario.Plan.of(E001Scenario.TUNING_SINGLE,
                List.of(E001Scenario.Center.of("single", 0, 0, 2)));
        List<E001Sample> samples = List.of(point(SEEDS.get(0), 0, 285), point(SEEDS.get(0), 1, 255),
                point(SEEDS.get(1), 0, 255), point(SEEDS.get(1), 1, 255), point(SEEDS.get(3), 0, 0));
        E001Batch batch = E001Batch.of(E001Parameters.distance("D", 120), plan, samples, List.of());
        E001Trial trial = E001Trial.of(batch, E001Evaluation.of(false, Map.of(), E001MetricSet.from(Map.of())));
        List<E001Sample> reversed = new ArrayList<>(samples);
        Collections.reverse(reversed);

        // when
        List<E001BoundaryObservation.Row> rows = trial.boundaryObservations();

        // then
        assertThat(rows).hasSize(6);
        assertThat(rows.getFirst().sampleSeed()).isNull();
        assertThat(rows.getFirst().requestedCount()).isEqualTo(10);
        assertThat(rows.getFirst().observation().generatedCount()).isEqualTo(5);
        assertThat(rows.getFirst().observation().rawDensityRatio()).isCloseTo(15300.0 / (3 * 17100.0), within(1.0e-15));
        assertThat(rows.subList(1, 6)).extracting(E001BoundaryObservation.Row::sampleSeed).containsExactlyElementsOf(SEEDS);
        assertThat(rows.subList(1, 6)).allSatisfy(row -> assertThat(row.requestedCount()).isEqualTo(2));
        assertThat(rows.get(3).observation().status()).isEqualTo(E001BoundaryObservation.Status.NO_SAMPLES);
        assertThat(batch.integrityPassed()).isFalse();
        assertThat(rows).isEqualTo(E001BoundaryObservation.from(
                E001Batch.of(batch.parameters(), plan, reversed, List.of())));
    }

    @Test
    void 다중_중심에서도_각_별의_입력_중심으로_경계_거리를_계산한다() {
        // given
        List<E001Sample> samples = List.of(
                E001Sample.of(SEEDS.getFirst(), "west", 0, 989550, 1969950, E001Offset.of(285, 0, 285)),
                E001Sample.of(SEEDS.getFirst(), "east", 0, 990150, 1969950, E001Offset.of(255, 0, 255)));

        // when
        E001BoundaryObservation observed = E001BoundaryObservation.of(samples, false);

        // then
        assertThat(observed.outerCount()).isEqualTo(1L);
        assertThat(observed.innerCount()).isEqualTo(1L);
    }

    private static List<E001Sample> bands(int total, int outer, int inner) {
        List<E001Sample> samples = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            samples.add(point(SEEDS.getFirst(), i, i < outer ? 285 : i < outer + inner ? 255 : 0));
        }
        return List.copyOf(samples);
    }

    private static E001Sample point(long seed, long index, double radius) {
        return E001Sample.of(seed, "single", index, 0, 0, E001Offset.of(radius, 0, radius));
    }
}
