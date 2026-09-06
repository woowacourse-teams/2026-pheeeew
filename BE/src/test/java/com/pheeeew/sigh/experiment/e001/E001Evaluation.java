package com.pheeeew.sigh.experiment.e001;

import java.util.Map;
import java.util.Set;

record E001Evaluation(boolean integrityPassed, Map<Long, E001MetricSet> bySeed, E001MetricSet pooled) {

    static final Set<Long> SAMPLE_SEEDS = Set.of(
            2_026_090_301L, 2_026_090_302L, 2_026_090_303L, 2_026_090_304L, 2_026_090_305L);

    E001Evaluation {
        bySeed = Map.copyOf(bySeed);
    }

    static E001Evaluation of(boolean integrityPassed, Map<Long, E001MetricSet> bySeed, E001MetricSet pooled) {
        return new E001Evaluation(integrityPassed, bySeed, pooled);
    }

    double median(String metric) {
        return E001Statistics.median(seedValues(metric));
    }

    double maximum(String metric) {
        return E001Statistics.quantile(seedValues(metric), 1.0);
    }

    double minimum(String metric) {
        double[] values = seedValues(metric);
        return values.length == 0 ? Double.NaN : E001Statistics.quantile(values, 1.0 / values.length);
    }

    double pooledValue(String metric) {
        double value = pooled.value(metric);
        return Double.isFinite(value) && value >= 0.0 ? value : Double.NaN;
    }

    int improvedSeeds(E001Evaluation baseline, String metric) {
        double[] candidateValues = seedValues(metric);
        double[] baselineValues = baseline.seedValues(metric);
        if (candidateValues.length != SAMPLE_SEEDS.size() || baselineValues.length != SAMPLE_SEEDS.size()) {
            return 0;
        }
        int improved = 0;
        for (int index = 0; index < candidateValues.length; index++) {
            if (candidateValues[index] < baselineValues[index]) {
                improved++;
            }
        }
        return improved;
    }

    private double[] seedValues(String metric) {
        if (!bySeed.keySet().equals(SAMPLE_SEEDS)) {
            return new double[0];
        }
        double[] values = bySeed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .mapToDouble(entry -> entry.getValue().value(metric)).toArray();
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0.0) {
                return new double[0];
            }
        }
        return values;
    }
}
