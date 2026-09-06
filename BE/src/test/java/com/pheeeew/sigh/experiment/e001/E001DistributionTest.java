package com.pheeeew.sigh.experiment.e001;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class E001DistributionTest {

    @Test
    void 정상_흐름의_요청_계약은_833000개이며_E는_D_선택_뒤에만_평가한다() {
        // given
        List<E001Parameters> calibrated = new ArrayList<>();
        List<String> calls = new ArrayList<>();

        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) -> {
            if (parameters.modelId().equals("E")) {
                assertThat(calls).hasSizeGreaterThanOrEqualTo(6);
                assertThat(baseline.batch().parameters()).isEqualTo(E001Parameters.distance("D", 120));
                assertThat(baseline.batch().plan()).isEqualTo(plan);
            }
            calls.add(parameters.modelId() + ":" + plan.scenario().id());
            return fixture(parameters, plan);
        }, parameters -> {
            calibrated.add(parameters);
            return 0.2;
        });

        // then
        assertThat(result.decision().distributionOutcome()).isEqualTo("e-review-ready");
        assertThat(result.requestedCount()).isEqualTo(833_000L);
        assertThat(result.trials()).hasSize(104);
        assertThat(calibrated).hasSize(36).doesNotHaveDuplicates();
        assertThat(result.decision().d().sigma()).isEqualTo(120);
        assertThat(result.decision().e().parameterSetId()).isEqualTo("e-s120-gradient-p1-b040-sir16");
        assertThat(result.trials().stream().filter(trial -> trial.batch().plan().scenario().phase().equals("tuning"))
                .mapToLong(trial -> trial.batch().plan().requestedCount()).sum()).isEqualTo(195_000L);
        // 이 테스트는 평가 결과 fixture만 사용하며 분포 좌표를 생성하지 않아요.
        assertThat(result.generatedCount()).isZero();
        assertThat(result.failureCount()).isZero();
    }

    @Test
    void D가_모두_탈락하면_E_보정과_확인과_spectral을_실행하지_않는다() {
        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) ->
                parameters.modelId().equals("D") ? metric(fixture(parameters, plan), "radius.p95", 211.0)
                        : fixture(parameters, plan), ignored -> {
            throw new AssertionError("D가 없으면 E field를 측정하면 안 돼요.");
        });

        // then
        assertThat(result.decision().reason()).isEqualTo("no-d");
        assertThat(result.requestedCount()).isEqualTo(15_000L);
        assertThat(result.trials()).hasSize(6);
    }

    @Test
    void D_100을_선택하면_C와_모든_E도_100으로_고정한다() {
        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) -> {
            E001Trial trial = fixture(parameters, plan);
            return parameters.modelId().equals("D") && parameters.sigma() == 120
                    ? metric(trial, "radius.p95", 211.0) : trial;
        }, ignored -> 0.2);

        // then
        assertThat(result.decision().d().sigma()).isEqualTo(100);
        assertThat(result.trials()).filteredOn(trial -> List.of("C", "E").contains(trial.batch().parameters().modelId()))
                .allSatisfy(trial -> assertThat(trial.batch().parameters().sigma()).isEqualTo(100));
    }

    @Test
    void E가_없으면_D를_확인한_뒤_끝내고_spectral을_만들지_않는다() {
        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) ->
                parameters.modelId().equals("E") ? metric(fixture(parameters, plan), "radialKs", 0.06)
                        : fixture(parameters, plan), ignored -> 0.2);

        // then
        assertThat(result.decision().distributionOutcome()).isEqualTo("selected-d");
        assertThat(result.decision().reason()).isEqualTo("no-e");
        assertThat(result.requestedCount()).isEqualTo(415_000L);
        assertThat(result.trials()).noneSatisfy(trial -> assertThat(trial.batch().plan().scenario().phase()).isEqualTo("spectral"));
    }

    @ParameterizedTest
    @CsvSource({"A, TUNING_SINGLE, integrity-failure", "B, CONFIRMATION_SINGLE_500, integrity-failure",
            "D, CONFIRMATION_SINGLE_500, integrity-failure", "E, CONFIRMATION_SINGLE_500, e-confirmation-failed",
            "A, SPECTRAL_EQUAL, integrity-failure", "D, SPECTRAL_EQUAL, integrity-failure",
            "E, SPECTRAL_EQUAL, e-spectral-failed"})
    void 기준_또는_선택_모델의_무결성_실패_뒤_추가_생성을_중단한다(String model, E001Scenario failedScenario, String reason) {
        // given
        List<String> calls = new ArrayList<>();

        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) -> {
            calls.add(parameters.modelId() + ":" + plan.scenario());
            E001Trial trial = fixture(parameters, plan);
            if (parameters.modelId().equals(model) && plan.scenario() == failedScenario) {
                return E001Trial.of(trial.batch(), E001Evaluation.of(false,
                        trial.evaluation().bySeed(), trial.evaluation().pooled()));
            }
            return trial;
        }, ignored -> 0.2);

        // then
        assertThat(result.decision().reason()).isEqualTo(reason);
        assertThat(calls.getLast()).isEqualTo(model + ":" + failedScenario);
        assertThat(result.trials()).hasSize(calls.size());
    }

    @Test
    void D_확인_수치가_실패하면_E_확인과_spectral을_건너뛴다() {
        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) -> {
            E001Trial trial = fixture(parameters, plan);
            return parameters.modelId().equals("D") && plan.scenario() == E001Scenario.CONFIRMATION_SINGLE_5000
                    ? metric(trial, "radius.p95", 211.0) : trial;
        }, ignored -> 0.2);

        // then
        assertThat(result.decision().reason()).isEqualTo("no-d");
        assertThat(result.trials()).noneSatisfy(trial -> {
            assertThat(trial.batch().parameters().modelId()).isEqualTo("E");
            assertThat(trial.batch().plan().scenario().phase()).isEqualTo("confirmation");
        });
        assertThat(result.trials()).noneSatisfy(trial -> assertThat(trial.batch().plan().scenario().phase()).isEqualTo("spectral"));
    }

    @Test
    void 선택된_E가_확인에서_실패해도_차순위_E로_다시_튜닝하지_않는다() {
        // when
        E001Distribution.Result result = E001Distribution.run((parameters, plan, baseline) -> {
            E001Trial trial = fixture(parameters, plan);
            return parameters.modelId().equals("E") && plan.scenario() == E001Scenario.CONFIRMATION_SINGLE_5000
                    ? metric(trial, "radialKs", 0.06) : trial;
        }, ignored -> 0.2);

        // then
        assertThat(result.decision().reason()).isEqualTo("e-confirmation-failed");
        assertThat(result.trials().stream().filter(trial -> trial.batch().parameters().modelId().equals("E")
                && trial.batch().plan().scenario().phase().equals("confirmation"))
                .map(trial -> trial.batch().parameters().parameterSetId()).distinct().toList()).hasSize(1);
    }

    @Test
    void 평가_결과를_다른_파라미터_요청에_연결하지_않는다() {
        // when & then
        assertThatThrownBy(() -> E001Distribution.run((parameters, plan, baseline) ->
                fixture(E001Parameters.distance("B", 0), plan), ignored -> 0.2)).isInstanceOf(IllegalStateException.class);
    }

    private static E001Trial fixture(E001Parameters parameters, E001Scenario.Plan plan) {
        boolean e = parameters.modelId().equals("E");
        E001MetricSet shape = E001MetricSet.from(Map.of("radius.p95", 200.0, "radius.p99", 240.0,
                "edgeRatio30", 0.2, "a4", e ? 0.1 : 0.4, "radialKs", 0.04, "seam300", e ? 0.7 : 1.0));
        Map<Long, E001MetricSet> seeds = new HashMap<>();
        E001Evaluation.SAMPLE_SEEDS.forEach(seed -> seeds.put(seed, shape));
        E001MetricSet pooled = E001MetricSet.from(Map.of("proximity16", 0.2, "neighbors10", 4.0,
                "hotspot50.p99", 9.0, "hotspot50.max", 12.0, "grid300", e ? 0.8 : 1.0));
        return E001Trial.of(E001Batch.of(parameters, plan, List.of(), List.of()), E001Evaluation.of(true, seeds, pooled));
    }

    private static E001Trial metric(E001Trial trial, String key, double value) {
        Map<Long, E001MetricSet> seeds = new HashMap<>();
        trial.evaluation().bySeed().forEach((seed, metric) -> {
            Map<String, Double> values = new HashMap<>(metric.values());
            values.put(key, value);
            seeds.put(seed, E001MetricSet.from(values));
        });
        return E001Trial.of(trial.batch(), E001Evaluation.of(true, seeds, trial.evaluation().pooled()));
    }
}
