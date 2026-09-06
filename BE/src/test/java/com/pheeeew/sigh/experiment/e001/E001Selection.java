package com.pheeeew.sigh.experiment.e001;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class E001Selection {

    static final String TUNING_SINGLE = "tuning-single-n500";
    static final String TUNING_GRID = "tuning-grid-imbalanced-n4500";
    static final List<String> CONFIRMATION_SINGLE = List.of(
            "confirmation-single-n500", "confirmation-single-n5000");
    static final List<String> CONFIRMATION_GRID = List.of(
            "confirmation-grid-equal-n500-per-center", "confirmation-grid-equal-n5000-per-center");
    static final List<String> SPECTRAL = List.of(
            "spectral-grid-equal-n500-per-center", "spectral-grid-imbalanced-n60500");
    static final List<String> REVIEW_SINGLE = List.of("review-ad-single-n500", "review-ad-single-n5000");
    static final List<String> REVIEW_GRID = List.of(
            "review-ad-grid-equal-n500-per-center", "review-ad-grid-equal-n5000-per-center");
    private static final List<String> SAMPLERS = List.of("sir16", "sir32", "rejection128");

    private E001Selection() {
    }

    static Decision tune(E001Candidate a, List<E001Candidate> dCandidates, List<E001Candidate> eCandidates) {
        if (!integrity(a, List.of(TUNING_SINGLE, TUNING_GRID))) {
            return Decision.of(Status.INCONCLUSIVE, "integrity-failure", null, null);
        }
        E001Candidate d = dCandidates.stream()
                .filter(candidate -> candidate.sigma() == 100 || candidate.sigma() == 120)
                .filter(candidate -> integrity(candidate, List.of(TUNING_SINGLE, TUNING_GRID)))
                .filter(candidate -> passesD(candidate.scenario(TUNING_SINGLE)))
                .sorted(Comparator.comparingInt(E001Candidate::sigma).reversed()
                        .thenComparing(E001Candidate::parameterSetId))
                .findFirst().orElse(null);
        if (d == null) {
            return Decision.of(Status.INCONCLUSIVE, "no-d", null, null);
        }
        E001Candidate e = eCandidates.stream()
                .filter(candidate -> candidate.sigma() == d.sigma())
                .filter(candidate -> SAMPLERS.contains(candidate.sampler()))
                .filter(candidate -> Double.isFinite(candidate.logIntensityStd()) && candidate.logIntensityStd() >= 0.0)
                .filter(candidate -> integrity(candidate, List.of(TUNING_SINGLE, TUNING_GRID)))
                .filter(candidate -> passesESingle(a.scenario(TUNING_SINGLE), candidate.scenario(TUNING_SINGLE)))
                .filter(candidate -> passesDensity(d.scenario(TUNING_GRID), candidate.scenario(TUNING_GRID)))
                .filter(candidate -> passesSeam(d.scenario(TUNING_GRID), candidate.scenario(TUNING_GRID)))
                .min(Comparator.comparingDouble(E001Candidate::logIntensityStd)
                        .thenComparingInt(candidate -> SAMPLERS.indexOf(candidate.sampler()))
                        .thenComparingDouble(candidate -> candidate.scenario(TUNING_GRID).median("seam300"))
                        .thenComparingDouble(candidate -> candidate.scenario(TUNING_SINGLE).median("radialKs"))
                        .thenComparing(E001Candidate::parameterSetId))
                .orElse(null);
        return Decision.of(Status.CONFIRMATION_REQUIRED, "", d, e);
    }

    static Decision confirm(Decision tuning, E001Candidate a, E001Candidate b, E001Candidate c,
                            E001Candidate d, E001Candidate e) {
        requireStage(tuning, Status.CONFIRMATION_REQUIRED, d, e);
        List<String> scenarios = Stream.concat(CONFIRMATION_SINGLE.stream(),
                CONFIRMATION_GRID.stream()).toList();
        if (!integrity(a, scenarios) || !integrity(b, scenarios) || !integrity(c, scenarios)
                || !integrity(d, scenarios)) {
            return Decision.of(Status.INCONCLUSIVE, "integrity-failure", d, e);
        }
        if (CONFIRMATION_SINGLE.stream().anyMatch(id -> !passesD(d.scenario(id)))) {
            return Decision.of(Status.INCONCLUSIVE, "no-d", d, e);
        }
        if (e == null) {
            return Decision.of(Status.SPECTRAL_REQUIRED, "no-e", d, null);
        }
        if (!integrity(e, scenarios)
                || CONFIRMATION_SINGLE.stream().anyMatch(id -> !passesESingle(a.scenario(id), e.scenario(id)))
                || CONFIRMATION_GRID.stream().anyMatch(id -> !passesDensity(d.scenario(id), e.scenario(id)))) {
            return Decision.of(Status.SPECTRAL_REQUIRED, "e-confirmation-failed", d, e);
        }
        return Decision.of(Status.SPECTRAL_REQUIRED, "", d, e);
    }

    static Decision spectral(Decision confirmation, E001Candidate a, E001Candidate d, E001Candidate e) {
        requireStage(confirmation, Status.SPECTRAL_REQUIRED, d, e);
        if (!integrity(a, SPECTRAL) || !integrity(d, SPECTRAL)) {
            return Decision.of(Status.INCONCLUSIVE, "integrity-failure", d, e);
        }
        if (SPECTRAL.stream().anyMatch(id -> !Double.isFinite(d.scenario(id).pooledValue("grid300")))) {
            return Decision.of(Status.INCONCLUSIVE, "no-d", d, e);
        }
        if (!confirmation.reason().isEmpty()) {
            return Decision.of(Status.REVIEW_REQUIRED, confirmation.reason(), d, e);
        }
        if (!integrity(e, SPECTRAL) || SPECTRAL.stream().anyMatch(id ->
                !atMost(e.scenario(id).pooledValue("grid300"), 0.8 * d.scenario(id).pooledValue("grid300")))) {
            return Decision.of(Status.REVIEW_REQUIRED, "e-spectral-failed", d, e);
        }
        return Decision.of(Status.REVIEW_REQUIRED, "", d, e);
    }

    static Decision review(Decision spectral, E001Candidate a, E001Candidate d, E001Candidate e) {
        requireStage(spectral, Status.REVIEW_REQUIRED, d, e);
        List<String> scenarios = Stream.concat(REVIEW_SINGLE.stream(), REVIEW_GRID.stream()).toList();
        if (!integrity(a, scenarios) || !integrity(d, scenarios)) {
            return Decision.of(Status.INCONCLUSIVE, "integrity-failure", d, e);
        }
        if (REVIEW_SINGLE.stream().anyMatch(id -> !passesD(d.scenario(id)))) {
            return Decision.of(Status.INCONCLUSIVE, "no-d", d, e);
        }
        return spectral.reason().isEmpty()
                ? Decision.of(Status.E_REVIEW_READY, "e-review-pending", d, e)
                : Decision.of(Status.D_REVIEW_READY, spectral.reason(), d, e);
    }

    static boolean passesD(E001Evaluation d) {
        return atMost(d.median("radius.p95"), 210.0)
                && atMost(d.median("radius.p99"), 250.0)
                && atMost(d.maximum("radius.p99"), 270.0);
    }

    static boolean passesESingle(E001Evaluation a, E001Evaluation e) {
        return atMost(e.median("radialKs"), 0.05)
                && atMost(e.median("a4"), 0.5 * a.median("a4"));
    }

    static boolean passesDensity(E001Evaluation d, E001Evaluation e) {
        return atMost(e.pooledValue("proximity16"), d.pooledValue("proximity16") + 0.03)
                && atMost(e.pooledValue("neighbors10"), 1.25 * d.pooledValue("neighbors10"))
                && atMost(e.pooledValue("hotspot50.max"), 4.0 * d.pooledValue("hotspot50.p99"));
    }

    static boolean passesSeam(E001Evaluation d, E001Evaluation e) {
        return atMost(e.median("seam300"), 0.8 * d.median("seam300")) && e.improvedSeeds(d, "seam300") >= 4;
    }

    private static boolean integrity(E001Candidate candidate, List<String> scenarios) {
        return candidate != null && scenarios.stream().allMatch(id -> candidate.scenario(id).integrityPassed());
    }

    private static boolean atMost(double value, double maximum) {
        return Double.isFinite(value) && Double.isFinite(maximum) && value >= 0.0 && maximum >= 0.0 && value <= maximum;
    }

    private static void requireStage(Decision previous, Status expected, E001Candidate d, E001Candidate e) {
        if (previous.status() != expected || !sameParameters(previous.d(), d) || !sameParameters(previous.e(), e)) {
            throw new IllegalArgumentException("이전 단계에서 고정한 D와 E를 같은 파라미터로 재검증해야 해요.");
        }
    }

    private static boolean sameParameters(E001Candidate left, E001Candidate right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.parameterSetId().equals(right.parameterSetId()) && left.sigma() == right.sigma()
                && left.sampler().equals(right.sampler())
                && Double.doubleToRawLongBits(left.logIntensityStd()) == Double.doubleToRawLongBits(right.logIntensityStd());
    }

    enum Status {
        CONFIRMATION_REQUIRED, SPECTRAL_REQUIRED, REVIEW_REQUIRED, INCONCLUSIVE, D_REVIEW_READY, E_REVIEW_READY
    }

    record Decision(Status status, String reason, E001Candidate d, E001Candidate e) {

        static Decision of(Status status, String reason, E001Candidate d, E001Candidate e) {
            return new Decision(status, reason, d, e);
        }

        String distributionOutcome() {
            return switch (status) {
                case INCONCLUSIVE -> "inconclusive";
                case D_REVIEW_READY -> "d-review-ready";
                case E_REVIEW_READY -> "e-review-ready";
                default -> throw new IllegalStateException("분포 판정이 아직 끝나지 않았어요.");
            };
        }
    }
}
