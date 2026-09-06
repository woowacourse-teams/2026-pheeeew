package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

record E001Parameters(String modelId, int sigma, E001Noise noise, E001FieldProfile profile, double beta, String sampler) {

    E001Parameters {
        if (!List.of("A", "B", "C", "D", "E").contains(modelId)
                || (List.of("A", "B").contains(modelId) ? sigma != 0 : sigma != 100 && sigma != 120)) {
            throw new IllegalArgumentException("사전등록된 모델과 sigma만 사용해야 해요.");
        }
        if (modelId.equals("E")) {
            if (noise == null || profile == null || !List.of(0.4, 0.7, 1.0).contains(beta)
                    || !List.of("sir16", "sir32", "rejection128").contains(sampler)) {
                throw new IllegalArgumentException("E는 사전등록된 field와 sampler를 사용해야 해요.");
            }
        } else if (noise != null || profile != null || beta != 0.0 || !sampler.isEmpty()) {
            throw new IllegalArgumentException("거리 모델에는 field 파라미터가 없어요.");
        }
    }

    static E001Parameters distance(String model, int sigma) {
        if (model.equals("E")) {
            throw new IllegalArgumentException("E는 field 파라미터가 필요해요.");
        }
        return new E001Parameters(model, sigma, null, null, 0.0, "");
    }

    static E001Parameters of(int sigma, E001Noise noise, E001FieldProfile profile, double beta, String sampler) {
        return new E001Parameters("E", sigma, noise, profile, beta, sampler);
    }

    static List<E001Parameters> fieldCandidates(int sigma) {
        List<E001Parameters> candidates = new ArrayList<>();
        for (E001Noise noise : E001Noise.values()) {
            for (E001FieldProfile profile : E001FieldProfile.values()) {
                for (double beta : List.of(0.4, 0.7, 1.0)) {
                    for (String sampler : List.of("sir16", "sir32", "rejection128")) {
                        candidates.add(of(sigma, noise, profile, beta, sampler));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparing(E001Parameters::parameterSetId));
        return List.copyOf(candidates);
    }

    String parameterSetId() {
        return switch (modelId) {
            case "A" -> "a-square-300";
            case "B" -> "b-disk-r300";
            case "C", "D" -> modelId.toLowerCase(Locale.ROOT) + "-s" + sigma + "-r300";
            default -> String.format(Locale.ROOT, "e-s%d-%s-%s-b%03d-%s", sigma,
                    noise.name().toLowerCase(Locale.ROOT), profile.name().toLowerCase(Locale.ROOT),
                    (int) StrictMath.round(beta * 100.0), sampler);
        };
    }

    PointSampler pointSampler() {
        return switch (modelId) {
            case "A" -> (random, x, y) -> E001DistanceSampler.sampleSquare(random);
            case "B" -> (random, x, y) -> E001DistanceSampler.sampleDisk(random);
            case "C" -> (random, x, y) -> E001DistanceSampler.sampleTruncatedGaussian(random, sigma);
            case "D" -> (random, x, y) -> E001DistanceSampler.sampleTaperedGaussian(random, sigma);
            default -> fieldSampler();
        };
    }

    double logIntensityStd() {
        if (!modelId.equals("E")) {
            return 0.0;
        }
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);
        return E001ShapeMetrics.logIntensityStd(field::valueAt, beta,
                E001Scenario.TUNING_SINGLE.originX(), E001Scenario.TUNING_SINGLE.originY());
    }

    private PointSampler fieldSampler() {
        E001MultiscaleField field = E001MultiscaleField.of(noise, profile, E001MultiscaleField.FIELD_SEED);
        E001FieldSampler generator = E001FieldSampler.of(sigma, beta, field::valueAt);
        return switch (sampler) {
            case "sir16" -> generator::sampleSir16;
            case "sir32" -> generator::sampleSir32;
            default -> generator::sampleRejection128;
        };
    }

    @FunctionalInterface
    interface PointSampler {

        E001SamplingResult sample(E001UniformRandom random, double centerX, double centerY);
    }
}
