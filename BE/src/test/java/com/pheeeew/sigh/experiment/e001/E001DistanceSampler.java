package com.pheeeew.sigh.experiment.e001;

final class E001DistanceSampler {

    static final int MAX_PROPOSALS = 4_096;

    private static final double MAX_RADIUS_METERS = 300.0;
    private static final double MAX_RADIUS_SQUARED_METERS = 90_000.0;
    private static final double GRID_SIZE_METERS = 300.0;
    private static final double GRID_HALF_SIZE_METERS = 150.0;
    private static final double TWO_PI = 0x1.921fb54442d18p2;

    private E001DistanceSampler() {
    }

    static E001SamplingResult sampleSquare(E001UniformRandom random) {
        double easting = (GRID_SIZE_METERS * random.nextDouble()) - GRID_HALF_SIZE_METERS;
        double northing = (GRID_SIZE_METERS * random.nextDouble()) - GRID_HALF_SIZE_METERS;
        double radius = StrictMath.hypot(easting, northing);
        E001Offset offset = E001Offset.of(easting, northing, radius);
        return E001SamplingResult.Success.of(offset, 1);
    }

    static E001SamplingResult sampleDisk(E001UniformRandom random) {
        for (int proposal = 1; proposal <= MAX_PROPOSALS; proposal++) {
            double radiusUniform = random.nextDouble();
            double angleUniform = random.nextDouble();
            double radius = MAX_RADIUS_METERS * StrictMath.sqrt(radiusUniform);
            double theta = TWO_PI * angleUniform;

            E001Offset offset = createPolarOffset(radius, theta);
            if (offset != null) {
                return E001SamplingResult.Success.of(offset, proposal);
            }
        }
        return E001SamplingResult.Failure.from(MAX_PROPOSALS);
    }

    static E001SamplingResult sampleTruncatedGaussian(E001UniformRandom random, double sigmaMeters) {
        validateSigma(sigmaMeters);

        for (int proposal = 1; proposal <= MAX_PROPOSALS; proposal++) {
            double radiusUniform = random.nextDouble();
            double angleUniform = random.nextDouble();
            double sigmaSquared = sigmaMeters * sigmaMeters;
            double denominator = 2.0 * sigmaSquared;
            double truncationExponent = -(MAX_RADIUS_SQUARED_METERS / denominator);
            double truncationDelta = StrictMath.expm1(truncationExponent);
            double scaledDelta = radiusUniform * truncationDelta;
            double logTerm = StrictMath.log1p(scaledDelta);
            double radicand = -2.0 * logTerm;
            double radius = sigmaMeters * StrictMath.sqrt(radicand);
            double theta = TWO_PI * angleUniform;

            E001Offset offset = createPolarOffset(radius, theta);
            if (offset != null) {
                return E001SamplingResult.Success.of(offset, proposal);
            }
        }
        return E001SamplingResult.Failure.from(MAX_PROPOSALS);
    }

    static E001SamplingResult sampleTaperedGaussian(E001UniformRandom random, double sigmaMeters) {
        validateSigma(sigmaMeters);

        for (int proposal = 1; proposal <= MAX_PROPOSALS; proposal++) {
            double radiusUniform = random.nextDouble();
            double angleUniform = random.nextDouble();
            double acceptanceUniform = random.nextDouble();
            double radius = MAX_RADIUS_METERS * StrictMath.sqrt(radiusUniform);
            double theta = TWO_PI * angleUniform;
            double radiusSquared = radius * radius;
            double sigmaSquared = sigmaMeters * sigmaMeters;
            double denominator = 2.0 * sigmaSquared;
            double gaussianExponent = -(radiusSquared / denominator);
            double gaussian = StrictMath.exp(gaussianExponent);
            double normalizedRadius = radius / MAX_RADIUS_METERS;
            double acceptance = gaussian * taper(normalizedRadius);

            E001Offset offset = createPolarOffset(radius, theta);
            if (offset != null && acceptanceUniform < acceptance) {
                return E001SamplingResult.Success.of(offset, proposal);
            }
        }
        return E001SamplingResult.Failure.from(MAX_PROPOSALS);
    }

    static double taper(double normalizedRadius) {
        if (normalizedRadius >= 1.0) {
            return 0.0;
        }

        double radiusSquared = normalizedRadius * normalizedRadius;
        double remaining = 1.0 - normalizedRadius;
        double remainingSquared = remaining * remaining;
        double remainingCubed = remainingSquared * remaining;
        double shape = ((6.0 * radiusSquared) + (3.0 * normalizedRadius)) + 1.0;
        return remainingCubed * shape;
    }

    private static E001Offset createPolarOffset(double radius, double theta) {
        double cosine = StrictMath.cos(theta);
        double sine = StrictMath.sin(theta);
        double easting = radius * cosine;
        double northing = radius * sine;
        double outputRadius = StrictMath.hypot(easting, northing);

        if (!Double.isFinite(easting)
                || !Double.isFinite(northing)
                || !Double.isFinite(outputRadius)
                || outputRadius >= MAX_RADIUS_METERS) {
            return null;
        }
        return E001Offset.of(easting, northing, outputRadius);
    }

    private static void validateSigma(double sigmaMeters) {
        if (!Double.isFinite(sigmaMeters) || sigmaMeters <= 0.0) {
            throw new IllegalArgumentException("sigma는 유한한 양수여야 해요.");
        }
    }
}
