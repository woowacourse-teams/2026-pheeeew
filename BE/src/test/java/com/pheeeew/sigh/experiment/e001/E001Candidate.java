package com.pheeeew.sigh.experiment.e001;

import java.util.Map;

record E001Candidate(String parameterSetId, int sigma, String sampler, double logIntensityStd,
                     Map<String, E001Evaluation> scenarios) {

    E001Candidate {
        scenarios = Map.copyOf(scenarios);
    }

    static E001Candidate of(String parameterSetId, int sigma, String sampler, double logIntensityStd,
                            Map<String, E001Evaluation> scenarios) {
        return new E001Candidate(parameterSetId, sigma, sampler, logIntensityStd, scenarios);
    }

    E001Evaluation scenario(String id) {
        return scenarios.getOrDefault(id, E001Evaluation.of(false, Map.of(), E001MetricSet.from(Map.of())));
    }
}
