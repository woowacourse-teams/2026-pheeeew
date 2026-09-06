package com.pheeeew.sigh.experiment.e001;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

record E001Trial(E001Batch batch, E001Evaluation evaluation) {

    static E001Trial of(E001Batch batch, E001Evaluation evaluation) {
        return new E001Trial(batch, evaluation);
    }

    List<E001BoundaryObservation.Row> boundaryObservations() {
        return E001BoundaryObservation.from(batch);
    }

    static E001Trial run(E001Parameters parameters, E001Scenario.Plan plan, E001Trial baseline) {
        return measure(E001Batch.generate(parameters, plan), baseline);
    }

    static E001Trial measure(E001Batch batch, E001Trial baseline) {
        E001Scenario scenario = batch.plan().scenario();
        boolean radialComparison = batch.parameters().modelId().equals("E") && scenario.halfWidth() == 300;
        if (radialComparison && (baseline == null || !baseline.batch().parameters().modelId().equals("D")
                || baseline.batch().parameters().sigma() != batch.parameters().sigma()
                || !baseline.batch().plan().equals(batch.plan()))) {
            throw new IllegalArgumentException("E의 반경 비교에는 같은 시나리오와 sigma의 D 표본이 필요해요.");
        }
        Map<Long, E001MetricSet> bySeed = new HashMap<>();
        for (long seed : E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList()) {
            List<E001Sample> samples = shard(batch, seed);
            Map<String, Double> values = new HashMap<>(E001ShapeMetrics.measure(samples,
                    batch.parameters().modelId().equals("A"), scenario.originX(), scenario.originY()).values());
            if (radialComparison) {
                values.put("radialKs", E001ShapeMetrics.radialKs(shard(baseline.batch(), seed), samples));
            }
            bySeed.put(seed, E001MetricSet.from(values));
        }
        Map<String, Double> pooled = new HashMap<>(E001DensityMetrics.measure(batch.samples(),
                scenario.originX(), scenario.originY(), scenario.halfWidth()).values());
        if (scenario.phase().equals("spectral")) {
            pooled.put("grid300", E001SpectralMetrics.grid300(batch.samples(), scenario.originX(), scenario.originY()));
        }
        pooled.put("samplerFailureCount", (double) batch.failures().size());
        return of(batch, E001Evaluation.of(batch.integrityPassed(), bySeed, E001MetricSet.from(pooled)));
    }

    private static List<E001Sample> shard(E001Batch batch, long seed) {
        return batch.samples().stream().filter(point -> point.sampleSeed() == seed).toList();
    }
}
