package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.List;

record E001BoundaryObservation(long generatedCount, Long outerCount, Long innerCount,
                               double outerArea, double innerArea, Status status) {

    static E001BoundaryObservation of(List<E001Sample> samples, boolean square) {
        double outerArea = square ? 32_400.0 : StrictMath.PI * 17_100.0;
        double innerArea = square ? 25_200.0 : StrictMath.PI * 15_300.0;
        if (samples.stream().anyMatch(sample -> !E001ShapeMetrics.valid(sample, square))) {
            return new E001BoundaryObservation(samples.size(), null, null, outerArea, innerArea, Status.INVALID_SAMPLE);
        }
        long outer = 0;
        long inner = 0;
        for (E001Sample sample : samples) {
            double distance = square
                    ? 150.0 - StrictMath.max(StrictMath.abs(sample.offset().eastingMeters()),
                    StrictMath.abs(sample.offset().northingMeters()))
                    : 300.0 - sample.radius();
            if (distance >= 0.0 && distance < 30.0) {
                outer++;
            } else if (distance >= 30.0 && distance < 60.0) {
                inner++;
            }
        }
        Status status = samples.isEmpty() ? Status.NO_SAMPLES
                : inner > 0 ? Status.INNER_OBSERVED : outer > 0 ? Status.OUTER_ONLY : Status.EMPTY_BANDS;
        return new E001BoundaryObservation(samples.size(), outer, inner, outerArea, innerArea, status);
    }

    static List<Row> from(E001Batch batch) {
        boolean square = batch.parameters().modelId().equals("A");
        long requestedPerSeed = batch.plan().centers().stream().mapToLong(E001Scenario.Center::perSeedCount).sum();
        List<Row> rows = new ArrayList<>();
        // 빈 sample_seed인 pooled 행을 먼저 두고, seed별 비율이 아닌 실제 개수로 다시 계산해요.
        rows.add(Row.of(null, batch.plan().requestedCount(), of(batch.samples(), square)));
        for (long seed : E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList()) {
            List<E001Sample> shard = batch.samples().stream().filter(sample -> sample.sampleSeed() == seed).toList();
            rows.add(Row.of(seed, requestedPerSeed, of(shard, square)));
        }
        return List.copyOf(rows);
    }

    Double outerShare() {
        return outerCount == null || generatedCount == 0 ? null : outerCount / (double) generatedCount;
    }

    Double innerShare() {
        return innerCount == null || generatedCount == 0 ? null : innerCount / (double) generatedCount;
    }

    Double rawDensityRatio() {
        return innerCount == null || innerCount == 0 ? null : (outerCount / outerArea) / (innerCount / innerArea);
    }

    String interpretation() {
        return "descriptive-only";
    }

    enum Status {
        INVALID_SAMPLE("invalid-sample"), NO_SAMPLES("no-samples"), EMPTY_BANDS("empty-bands"),
        OUTER_ONLY("outer-only"), INNER_OBSERVED("inner-observed");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    record Row(Long sampleSeed, long requestedCount, E001BoundaryObservation observation) {

        static Row of(Long sampleSeed, long requestedCount, E001BoundaryObservation observation) {
            return new Row(sampleSeed, requestedCount, observation);
        }
    }
}
