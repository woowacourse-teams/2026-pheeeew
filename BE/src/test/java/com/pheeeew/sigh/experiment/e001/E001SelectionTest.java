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
import org.junit.jupiter.params.provider.ValueSource;

class E001SelectionTest {

    private static final E001Candidate A = candidate("a-square-300", 0, "", 0.0, false);
    private static final E001Candidate B = candidate("b-disk-r300", 0, "", 0.0, false);
    private static final E001Candidate C = candidate("c-s120-r300", 120, "", 0.0, false);
    private static final E001Candidate D = candidate("d-s120-r300", 120, "", 0.0, false);
    private static final E001Candidate E = candidate("e-s120-gradient-p1-b070-sir16", 120, "sir16", 0.2, true);

    @Test
    void D는_120미터를_우선하고_실패할_때만_100미터를_고른다() {
        // given
        E001Candidate d100 = candidate("d-s100-r300", 100, "", 0.0, false);
        E001Candidate failed = metric(D, E001Selection.TUNING_SINGLE, "radius.p95", 211.0);

        // when & then
        assertThat(E001Selection.tune(A, List.of(d100, D), List.of()).d()).isEqualTo(D);
        assertThat(E001Selection.tune(A, List.of(failed, d100), List.of()).d()).isEqualTo(d100);
        assertThat(E001Selection.tune(A, List.of(failed), List.of()).reason()).isEqualTo("no-d");
        assertThat(E001Selection.tune(A, List.of(), List.of(E)).status()).isEqualTo(E001Selection.Status.INCONCLUSIVE);
    }

    @ParameterizedTest
    @CsvSource({"radius.p95, 210", "radius.p99, 250"})
    void D_median_합격선은_경계까지_포함하고_다음_double은_탈락한다(String key, double limit) {
        // given
        E001Evaluation equal = metric(D, E001Selection.TUNING_SINGLE, key, limit).scenario(E001Selection.TUNING_SINGLE);
        E001Evaluation above = metric(D, E001Selection.TUNING_SINGLE, key, Math.nextUp(limit)).scenario(E001Selection.TUNING_SINGLE);

        // when & then
        assertThat(E001Selection.passesD(equal)).isTrue();
        assertThat(E001Selection.passesD(above)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"radius.p99, 270"})
    void D는_한_seed만_상한을_초과해도_탈락한다(String key, double limit) {
        // given
        E001Evaluation baseline = D.scenario(E001Selection.TUNING_SINGLE);
        E001Evaluation equal = seedMetric(baseline, 2_026_090_301L, key, limit);
        E001Evaluation above = seedMetric(baseline, 2_026_090_301L, key, Math.nextUp(limit));

        // when & then
        assertThat(E001Selection.passesD(equal)).isTrue();
        assertThat(E001Selection.passesD(above)).isFalse();
    }

    @Test
    void seed_누락과_nonfinite와_음수는_합격으로_처리하지_않는다() {
        // given
        E001Evaluation baseline = D.scenario(E001Selection.TUNING_SINGLE);
        Map<Long, E001MetricSet> missing = new HashMap<>(baseline.bySeed());
        missing.remove(2_026_090_301L);

        // when & then
        assertThat(E001Selection.passesD(E001Evaluation.of(true, missing, baseline.pooled()))).isFalse();
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0}) {
            assertThat(E001Selection.passesD(seedMetric(baseline, 2_026_090_301L, "radius.p95", invalid))).isFalse();
        }
        assertThat(E001Selection.passesD(E001Evaluation.of(true, Map.of(), E001MetricSet.from(Map.of())))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"single, radialKs, 0.05", "single, a4, 0.2", "grid, proximity16, 0.23",
            "grid, neighbors10, 5.0", "grid, hotspot50.max, 36.0", "grid, seam300, 0.8"})
    void E_합격선은_같은_D와_A의_지표에_상대적으로_적용한다(String kind, String key, double limit) {
        // given
        String scenario = kind.equals("single") ? E001Selection.TUNING_SINGLE : E001Selection.TUNING_GRID;
        E001Candidate equal = metric(E, scenario, key, limit);
        E001Candidate above = metric(E, scenario, key, Math.nextUp(limit));

        // when & then
        assertThat(E001Selection.tune(A, List.of(D), List.of(equal)).e()).isEqualTo(equal);
        assertThat(E001Selection.tune(A, List.of(D), List.of(above)).e()).isNull();
    }

    @Test
    void 경계_진단의_누락이나_값으로_D와_E_합격을_바꾸지_않는다() {
        // given
        assertThat(D.scenario(E001Selection.TUNING_SINGLE).median("edgeRatio30")).isNaN();
        for (double ignored : new double[]{0.0, 0.8947368421052633, 71.578947, Double.NaN}) {
            E001Candidate d = metric(D, E001Selection.TUNING_SINGLE, "edgeRatio30", ignored);
            E001Candidate e = metric(E, E001Selection.TUNING_SINGLE, "edgeRatio30", ignored);

            // when & then
            assertThat(E001Selection.tune(A, List.of(d), List.of(e)).d()).isEqualTo(d);
            assertThat(E001Selection.tune(A, List.of(d), List.of(e)).e()).isEqualTo(e);
        }
        assertThat(E001Selection.tune(A, List.of(D), List.of(E)).e()).isEqualTo(E);
    }

    @Test
    void seam은_median_개선뿐_아니라_동일_seed_네_개의_엄격한_개선이_필요하다() {
        // given
        E001Evaluation d = D.scenario(E001Selection.TUNING_GRID);
        E001Evaluation four = seedMetric(E.scenario(E001Selection.TUNING_GRID), 2_026_090_301L, "seam300", 1.0);
        E001Evaluation three = seedMetric(four, 2_026_090_302L, "seam300", 1.0);

        // when & then
        assertThat(E001Selection.passesSeam(d, four)).isTrue();
        assertThat(E001Selection.passesSeam(d, three)).isFalse();
        assertThat(three.median("seam300")).isEqualTo(0.7);
    }

    @ParameterizedTest
    @CsvSource({
            "0.1, rejection128, 0.7, 0.04, z, 0.2, sir16, 0.6, 0.03, a",
            "0.2, sir16, 0.7, 0.04, z, 0.2, sir32, 0.6, 0.03, a",
            "0.2, sir32, 0.7, 0.04, z, 0.2, rejection128, 0.6, 0.03, a",
            "0.2, sir16, 0.6, 0.04, z, 0.2, sir16, 0.7, 0.03, a",
            "0.2, sir16, 0.7, 0.03, z, 0.2, sir16, 0.7, 0.04, a",
            "0.2, sir16, 0.7, 0.04, a, 0.2, sir16, 0.7, 0.04, z"
    })
    void E는_강도_sampler_seam_KS_ID_순서로_결정적으로_정렬한다(
            double intensity1, String sampler1, double seam1, double ks1, String id1,
            double intensity2, String sampler2, double seam2, double ks2, String id2
    ) {
        // given
        E001Candidate first = ranked(id1, intensity1, sampler1, seam1, ks1);
        E001Candidate second = ranked(id2, intensity2, sampler2, seam2, ks2);

        // when & then
        assertThat(E001Selection.tune(A, List.of(D), List.of(second, first)).e()).isEqualTo(first);
        assertThat(E001Selection.tune(A, List.of(D), List.of(first, second)).e()).isEqualTo(first);
    }

    @Test
    void E는_다른_sigma와_누락된_강도와_알수없는_sampler를_선택하지_않는다() {
        // given
        List<E001Candidate> candidates = List.of(candidate("wrong-sigma", 100, "sir16", 0.1, true),
                candidate("nan-intensity", 120, "sir16", Double.NaN, true),
                candidate("unknown-sampler", 120, "other", 0.1, true));

        // when & then
        assertThat(E001Selection.tune(A, List.of(D), candidates).e()).isNull();
    }

    @Test
    void 튜닝_무결성은_A_실패와_후보_탈락을_구분한다() {
        // when & then
        assertThat(E001Selection.tune(missing(A, E001Selection.TUNING_GRID), List.of(D), List.of(E)).reason())
                .isEqualTo("integrity-failure");
        assertThat(E001Selection.tune(A, List.of(missing(D, E001Selection.TUNING_GRID)), List.of(E)).reason())
                .isEqualTo("no-d");
        assertThat(E001Selection.tune(A, List.of(D), List.of(missing(E, E001Selection.TUNING_GRID))).e()).isNull();
    }

    @Test
    void E가_없어도_D_확인_뒤_spectral과_추가_확인을_거쳐야_평가_후보가_된다() {
        // given
        E001Selection.Decision tuning = E001Selection.tune(A, List.of(D), List.of());
        E001Candidate failed = metric(D, E001Selection.CONFIRMATION_SINGLE.getLast(), "radius.p95", 211.0);

        // when & then
        E001Selection.Decision confirmation = E001Selection.confirm(tuning, A, B, C, D, null);
        E001Selection.Decision spectral = E001Selection.spectral(confirmation, A, D, null);
        assertThat(confirmation.status()).isEqualTo(E001Selection.Status.SPECTRAL_REQUIRED);
        assertThat(confirmation.reason()).isEqualTo("no-e");
        assertThatThrownBy(confirmation::distributionOutcome).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(spectral::distributionOutcome).isInstanceOf(IllegalStateException.class);
        assertThat(E001Selection.review(spectral, A, D, null).distributionOutcome()).isEqualTo("d-review-ready");
        assertThat(E001Selection.review(spectral, A, D, null).reason()).isEqualTo("no-e");
        assertThat(E001Selection.confirm(tuning, A, B, C, failed, null).reason()).isEqualTo("no-d");
        assertThatThrownBy(tuning::distributionOutcome).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirmation-single-n500", "confirmation-single-n5000",
            "confirmation-grid-equal-n500-per-center", "confirmation-grid-equal-n5000-per-center"})
    void 확인은_모든_scenario에서_대조군_D_무결성과_E_실패를_구분한다(String scenario) {
        // given
        E001Selection.Decision tuning = E001Selection.tune(A, List.of(D), List.of(E));

        // when & then
        assertThat(E001Selection.confirm(tuning, missing(A, scenario), B, C, D, E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.confirm(tuning, A, missing(B, scenario), C, D, E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.confirm(tuning, A, B, missing(C, scenario), D, E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.confirm(tuning, A, B, C, missing(D, scenario), E).status()).isEqualTo(E001Selection.Status.INCONCLUSIVE);
        assertThat(E001Selection.confirm(tuning, A, B, C, D, missing(E, scenario)).reason()).isEqualTo("e-confirmation-failed");
        String key = scenario.contains("single") ? "radialKs" : "neighbors10";
        assertThat(E001Selection.confirm(tuning, A, B, C, D, metric(E, scenario, key, 100.0)).reason())
                .isEqualTo("e-confirmation-failed");
    }

    @Test
    void 균등_확인_scenario에는_불균형_seam_gate를_재적용하지_않는다() {
        // given
        E001Selection.Decision tuning = E001Selection.tune(A, List.of(D), List.of(E));
        E001Candidate changed = E;
        for (String scenario : E001Selection.CONFIRMATION_GRID) {
            changed = metric(changed, scenario, "seam300", 2.0);
        }

        // when & then
        assertThat(E001Selection.confirm(tuning, A, B, C, D, changed).status()).isEqualTo(E001Selection.Status.SPECTRAL_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spectral-grid-equal-n500-per-center", "spectral-grid-imbalanced-n60500"})
    void spectral은_두_profile의_무결성과_20퍼센트_개선을_각각_요구한다(String scenario) {
        // given
        E001Selection.Decision tuning = E001Selection.tune(A, List.of(D), List.of(E));
        E001Selection.Decision confirmation = E001Selection.confirm(tuning, A, B, C, D, E);

        // when & then
        assertThat(E001Selection.spectral(confirmation, A, D, E).status()).isEqualTo(E001Selection.Status.REVIEW_REQUIRED);
        assertThat(E001Selection.spectral(confirmation, missing(A, scenario), D, E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.spectral(confirmation, A, missing(D, scenario), E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.spectral(confirmation, A, D, missing(E, scenario)).reason()).isEqualTo("e-spectral-failed");
        for (double invalid : new double[]{Math.nextUp(0.8), Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(E001Selection.spectral(confirmation, A, D, metric(E, scenario, "grid300", invalid)).reason())
                    .isEqualTo("e-spectral-failed");
        }
        assertThat(E001Selection.spectral(confirmation, A, metric(D, scenario, "grid300", Double.NaN), E).status())
                .isEqualTo(E001Selection.Status.INCONCLUSIVE);
        assertThat(E001Selection.spectral(confirmation, A, metric(D, scenario, "grid300", Double.NaN), E).reason())
                .isEqualTo("no-d");
        assertThat(E001Selection.spectral(confirmation, metric(A, scenario, "grid300", Double.NaN), D, E).status())
                .isEqualTo(E001Selection.Status.REVIEW_REQUIRED);
    }

    @Test
    void 확인_도중_파라미터_변경이나_단계_건너뛰기는_허용하지_않는다() {
        // given
        E001Selection.Decision tuning = E001Selection.tune(A, List.of(D), List.of(E));
        E001Candidate retuned = candidate("another-e", 120, "sir16", 0.1, true);

        // when & then
        assertThatThrownBy(() -> E001Selection.confirm(tuning, A, B, C, D, retuned))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> E001Selection.spectral(tuning, A, D, E))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> E001Selection.review(tuning, A, D, E))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"review-ad-single-n500", "review-ad-single-n5000",
            "review-ad-grid-equal-n500-per-center", "review-ad-grid-equal-n5000-per-center"})
    void 추가_AD_확인의_무결성과_거리_검사_이전에는_평가_준비가_아니다(String scenario) {
        // given
        E001Selection.Decision confirmation = E001Selection.confirm(E001Selection.tune(A, List.of(D), List.of(E)), A, B, C, D, E);
        E001Selection.Decision spectral = E001Selection.spectral(confirmation, A, D, E);

        // when & then
        assertThat(E001Selection.review(spectral, A, D, E).distributionOutcome()).isEqualTo("e-review-ready");
        assertThat(E001Selection.review(spectral, missing(A, scenario), D, E).reason()).isEqualTo("integrity-failure");
        assertThat(E001Selection.review(spectral, A, missing(D, scenario), E).reason()).isEqualTo("integrity-failure");
        if (scenario.contains("single")) {
            assertThat(E001Selection.review(spectral, A, metric(D, scenario, "radius.p95", 211.0), E).reason()).isEqualTo("no-d");
        }
        assertThatThrownBy(() -> E001Selection.review(spectral, A,
                candidate("d-s100-r300", 100, "", 0.0, false), E)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void E_확인_탈락_이유는_D의_spectral과_추가_확인_뒤에도_유지한다() {
        // given
        E001Candidate failed = missing(E, E001Selection.CONFIRMATION_SINGLE.getFirst());
        E001Selection.Decision confirmation = E001Selection.confirm(
                E001Selection.tune(A, List.of(D), List.of(E)), A, B, C, D, failed);

        // when
        E001Selection.Decision spectral = E001Selection.spectral(confirmation, A, D, failed);
        E001Selection.Decision result = E001Selection.review(spectral, A, D, failed);

        // then
        assertThat(result.distributionOutcome()).isEqualTo("d-review-ready");
        assertThat(result.reason()).isEqualTo("e-confirmation-failed");
        assertThat(result.e()).isEqualTo(failed);
    }

    private static E001Candidate candidate(String id, int sigma, String sampler, double intensity, boolean e) {
        Map<String, Double> values = Map.of("radius.p95", 200.0, "radius.p99", 240.0,
                "a4", e ? 0.1 : 0.4, "radialKs", 0.04, "seam300", e ? 0.7 : 1.0);
        Map<Long, E001MetricSet> seeds = new HashMap<>();
        E001Evaluation.SAMPLE_SEEDS.forEach(seed -> seeds.put(seed, E001MetricSet.from(values)));
        E001MetricSet pooled = E001MetricSet.from(Map.of("proximity16", 0.2, "neighbors10", 4.0,
                "hotspot50.p99", 9.0, "hotspot50.max", 12.0, "grid300", e ? 0.8 : 1.0));
        E001Evaluation evaluation = E001Evaluation.of(true, seeds, pooled);
        Map<String, E001Evaluation> scenarios = new HashMap<>();
        List<String> ids = new ArrayList<>(List.of(E001Selection.TUNING_SINGLE, E001Selection.TUNING_GRID));
        ids.addAll(E001Selection.CONFIRMATION_SINGLE);
        ids.addAll(E001Selection.CONFIRMATION_GRID);
        ids.addAll(E001Selection.SPECTRAL);
        ids.addAll(E001Selection.REVIEW_SINGLE);
        ids.addAll(E001Selection.REVIEW_GRID);
        ids.forEach(scenario -> scenarios.put(scenario, evaluation));
        return E001Candidate.of(id, sigma, sampler, intensity, scenarios);
    }

    private static E001Candidate ranked(String id, double intensity, String sampler, double seam, double ks) {
        E001Candidate candidate = candidate(id, 120, sampler, intensity, true);
        return metric(metric(candidate, E001Selection.TUNING_GRID, "seam300", seam),
                E001Selection.TUNING_SINGLE, "radialKs", ks);
    }

    private static E001Candidate metric(E001Candidate source, String scenario, String key, double value) {
        E001Evaluation current = source.scenario(scenario);
        Map<Long, E001MetricSet> seeds = new HashMap<>();
        current.bySeed().forEach((seed, metrics) -> seeds.put(seed, metricSet(metrics, key, value)));
        Map<String, E001Evaluation> scenarios = new HashMap<>(source.scenarios());
        scenarios.put(scenario, E001Evaluation.of(current.integrityPassed(), seeds, metricSet(current.pooled(), key, value)));
        return E001Candidate.of(source.parameterSetId(), source.sigma(), source.sampler(), source.logIntensityStd(), scenarios);
    }

    private static E001Evaluation seedMetric(E001Evaluation source, long seed, String key, double value) {
        Map<Long, E001MetricSet> seeds = new HashMap<>(source.bySeed());
        seeds.put(seed, metricSet(seeds.get(seed), key, value));
        return E001Evaluation.of(source.integrityPassed(), seeds, source.pooled());
    }

    private static E001MetricSet metricSet(E001MetricSet source, String key, double value) {
        Map<String, Double> values = new HashMap<>(source.values());
        values.put(key, value);
        return E001MetricSet.from(values);
    }

    private static E001Candidate missing(E001Candidate source, String scenario) {
        Map<String, E001Evaluation> scenarios = new HashMap<>(source.scenarios());
        scenarios.remove(scenario);
        return E001Candidate.of(source.parameterSetId(), source.sigma(), source.sampler(), source.logIntensityStd(), scenarios);
    }
}
