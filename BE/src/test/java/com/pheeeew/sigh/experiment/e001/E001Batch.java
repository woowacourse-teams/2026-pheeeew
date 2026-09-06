package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.List;

record E001Batch(E001Parameters parameters, E001Scenario.Plan plan,
                 List<E001Sample> samples, List<Failure> failures) {

    E001Batch {
        samples = List.copyOf(samples);
        failures = List.copyOf(failures);
    }

    static E001Batch of(E001Parameters parameters, E001Scenario.Plan plan,
                        List<E001Sample> samples, List<Failure> failures) {
        return new E001Batch(parameters, plan, samples, failures);
    }

    static E001Batch generate(E001Parameters parameters, E001Scenario.Plan plan) {
        return generate(parameters, plan, parameters.pointSampler());
    }

    static E001Batch generate(E001Parameters parameters, E001Scenario.Plan plan, E001Parameters.PointSampler sampler) {
        List<E001Sample> samples = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        E001Scenario scenario = plan.scenario();
        for (long seed : E001Evaluation.SAMPLE_SEEDS.stream().sorted().toList()) {
            for (E001Scenario.Center center : plan.centers()) {
                double x = scenario.originX() + 300.0 * center.i();
                double y = scenario.originY() + 300.0 * center.j();
                for (long index = 0; index < center.perSeedCount(); index++) {
                    long pointSeed = E001PointSeed.derive(scenario.id(), scenario.originId(), center.i(), center.j(), seed, index);
                    E001SamplingResult result = sampler.sample(E001SplitMix64.from(pointSeed), x, y);
                    if (result instanceof E001SamplingResult.Success success) {
                        samples.add(E001Sample.of(seed, center.id(), index, x, y, success.offset()));
                    } else {
                        failures.add(Failure.of(seed, center.id(), index, result.proposalCount()));
                    }
                }
            }
        }
        return of(parameters, plan, samples, failures);
    }

    boolean integrityPassed() {
        return E001Integrity.passes(plan.requested(), samples, failures.size(), parameters.modelId().equals("A"));
    }

    record Failure(long sampleSeed, String centerId, long pointIndex, int attemptLimit) {

        static Failure of(long sampleSeed, String centerId, long pointIndex, int attemptLimit) {
            return new Failure(sampleSeed, centerId, pointIndex, attemptLimit);
        }
    }
}
