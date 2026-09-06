package com.pheeeew.sigh.experiment.e001;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

/**
 * 성공 결과의 proposalCount는 생성한 D 후보 수예요.
 * D 내부 실패는 한도 4,096을 전달하고, E rejection 소진은 한도 128을 반환해요.
 */
final class E001FieldSampler {

    private static final int MAX_REJECTION_CANDIDATES = 128;

    private final double sigmaMeters;
    private final double beta;
    private final DoubleBinaryOperator field;

    private E001FieldSampler(double sigmaMeters, double beta, DoubleBinaryOperator field) {
        if (!Double.isFinite(sigmaMeters) || sigmaMeters <= 0.0) {
            throw new IllegalArgumentException("sigma는 유한한 양수여야 해요.");
        }
        if (!Double.isFinite(beta) || beta < 0.0) {
            throw new IllegalArgumentException("beta는 유한한 0 이상의 값이어야 해요.");
        }
        this.sigmaMeters = sigmaMeters;
        this.beta = beta;
        this.field = Objects.requireNonNull(field);
    }

    static E001FieldSampler of(double sigmaMeters, double beta, DoubleBinaryOperator field) {
        return new E001FieldSampler(sigmaMeters, beta, field);
    }

    E001SamplingResult sampleSir16(E001UniformRandom random, double centerEasting, double centerNorthing) {
        return sampleSir(random, centerEasting, centerNorthing, 16);
    }

    E001SamplingResult sampleSir32(E001UniformRandom random, double centerEasting, double centerNorthing) {
        return sampleSir(random, centerEasting, centerNorthing, 32);
    }

    E001SamplingResult sampleRejection128(E001UniformRandom random, double centerEasting, double centerNorthing) {
        for (int proposal = 1; proposal <= MAX_REJECTION_CANDIDATES; proposal++) {
            E001SamplingResult candidate = E001DistanceSampler.sampleTaperedGaussian(random, sigmaMeters);
            if (candidate instanceof E001SamplingResult.Failure) {
                return candidate;
            }
            E001Offset offset = ((E001SamplingResult.Success) candidate).offset();
            double value = fieldValue(centerEasting, centerNorthing, offset);
            double uniform = random.nextDouble();
            if (uniform < StrictMath.exp(beta * (value - 1.0))) {
                return E001SamplingResult.Success.of(offset, proposal);
            }
        }
        return E001SamplingResult.Failure.from(MAX_REJECTION_CANDIDATES);
    }

    private E001SamplingResult sampleSir(
            E001UniformRandom random,
            double centerEasting,
            double centerNorthing,
            int candidateCount
    ) {
        E001Offset[] candidates = new E001Offset[candidateCount];
        for (int index = 0; index < candidateCount; index++) {
            E001SamplingResult candidate = E001DistanceSampler.sampleTaperedGaussian(random, sigmaMeters);
            if (candidate instanceof E001SamplingResult.Failure) {
                return candidate;
            }
            candidates[index] = ((E001SamplingResult.Success) candidate).offset();
        }

        double[] logWeights = new double[candidateCount];
        double maxLogWeight = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < candidateCount; index++) {
            logWeights[index] = beta * fieldValue(centerEasting, centerNorthing, candidates[index]);
            maxLogWeight = StrictMath.max(maxLogWeight, logWeights[index]);
        }
        double[] weights = new double[candidateCount];
        double weightSum = 0.0;
        for (int index = 0; index < candidateCount; index++) {
            weights[index] = StrictMath.exp(logWeights[index] - maxLogWeight);
            weightSum += weights[index];
        }

        double target = random.nextDouble() * weightSum;
        double cumulative = 0.0;
        for (int index = 0; index < candidateCount - 1; index++) {
            cumulative += weights[index];
            if (target < cumulative) {
                return E001SamplingResult.Success.of(candidates[index], candidateCount);
            }
        }
        return E001SamplingResult.Success.of(candidates[candidateCount - 1], candidateCount);
    }

    private double fieldValue(double centerEasting, double centerNorthing, E001Offset offset) {
        double value = field.applyAsDouble(
                centerEasting + offset.eastingMeters(),
                centerNorthing + offset.northingMeters()
        );
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException("field 값은 유한한 [-1, 1] 범위여야 해요.");
        }
        return value;
    }
}
