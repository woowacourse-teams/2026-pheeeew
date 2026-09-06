package com.pheeeew.sigh.experiment.e001;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

final class E001ShapeMetrics {

    private E001ShapeMetrics() {
    }

    static E001MetricSet measure(List<E001Sample> samples, boolean square, double originX, double originY) {
        List<E001Sample> ordered = samples.stream().sorted(E001Sample.ORDER).toList();
        requireSingleSeed(ordered);
        double[] radii = ordered.stream().mapToDouble(E001Sample::radius).toArray();
        long violations = ordered.stream().filter(point -> !valid(point, square)).count();
        return E001MetricSet.from(Map.of(
                "radius.p50", E001Statistics.quantile(radii, 0.50),
                "radius.p95", E001Statistics.quantile(radii, 0.95),
                "radius.p99", E001Statistics.quantile(radii, 0.99),
                "radius.max", E001Statistics.quantile(radii, 1.0),
                "radius.violations", (double) violations,
                "edgeRatio30", violations == 0 ? edgeRatio(ordered, square) : Double.NaN,
                "a4", violations == 0 ? a4(ordered) : Double.NaN,
                "seam300", violations == 0 ? seam(ordered, originX, originY) : Double.NaN
        ));
    }

    static boolean valid(E001Sample sample, boolean square) {
        double dx = sample.offset().eastingMeters();
        double dy = sample.offset().northingMeters();
        double r = sample.radius();
        double sampledRadius = sample.offset().radiusMeters();
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(r)
                || !Double.isFinite(sampledRadius) || sampledRadius < 0.0
                || !Double.isFinite(sample.x()) || !Double.isFinite(sample.y())) {
            return false;
        }
        if (square) {
            return dx >= -150.0 && dx < 150.0 && dy >= -150.0 && dy < 150.0;
        }
        return r >= 0.0 && r < 300.0 && sampledRadius < 300.0;
    }

    static double radialKs(List<E001Sample> d, List<E001Sample> e) {
        requireSingleSeed(d);
        requireSingleSeed(e);
        if (d.isEmpty() || e.isEmpty()) {
            return Double.NaN;
        }
        if (d.getFirst().sampleSeed() != e.getFirst().sampleSeed()) {
            throw new IllegalArgumentException("radialKs는 같은 seed끼리 비교해야 해요.");
        }
        double[] left = d.stream().mapToDouble(E001Sample::radius).sorted().toArray();
        double[] right = e.stream().mapToDouble(E001Sample::radius).sorted().toArray();
        if (!Double.isFinite(left[left.length - 1]) || !Double.isFinite(right[right.length - 1])) {
            return Double.NaN;
        }
        int i = 0;
        int j = 0;
        double maximum = 0.0;
        while (i < left.length || j < right.length) {
            double nextLeft = i < left.length ? left[i] : Double.POSITIVE_INFINITY;
            double nextRight = j < right.length ? right[j] : Double.POSITIVE_INFINITY;
            double threshold = StrictMath.min(nextLeft, nextRight);
            while (i < left.length && left[i] <= threshold) {
                i++;
            }
            while (j < right.length && right[j] <= threshold) {
                j++;
            }
            double difference = StrictMath.abs(i / (double) left.length - j / (double) right.length);
            maximum = StrictMath.max(maximum, difference);
        }
        return maximum;
    }

    static double logIntensityStd(DoubleBinaryOperator field, double beta, double originX, double originY) {
        double[] values = new double[1_681];
        int index = 0;
        for (int j = -20; j <= 20; j++) {
            for (int i = -20; i <= 20; i++) {
                values[index++] = beta * field.applyAsDouble(originX + 30.0 * i, originY + 30.0 * j);
            }
        }
        return E001Statistics.populationStd(values);
    }

    private static void requireSingleSeed(List<E001Sample> samples) {
        if (!samples.isEmpty() && samples.stream().anyMatch(p -> p.sampleSeed() != samples.getFirst().sampleSeed())) {
            throw new IllegalArgumentException("분포 형태 지표에는 한 seed의 표본만 전달해야 해요.");
        }
    }

    private static double edgeRatio(List<E001Sample> samples, boolean square) {
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
        double outerArea = square ? 32_400.0 : StrictMath.PI * 17_100.0;
        double innerArea = square ? 25_200.0 : StrictMath.PI * 15_300.0;
        double outerDensity = (outer + 0.5) / outerArea;
        double innerDensity = (inner + 0.5) / innerArea;
        return outerDensity / innerDensity;
    }

    private static double a4(List<E001Sample> samples) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        double sumCos = 0.0;
        double sumSin = 0.0;
        for (E001Sample sample : samples) {
            double cos4 = 1.0;
            double sin4 = 0.0;
            if (sample.radius() != 0.0) {
                double theta = StrictMath.atan2(sample.offset().northingMeters(), sample.offset().eastingMeters());
                double angle4 = 4.0 * theta;
                cos4 = StrictMath.cos(angle4);
                sin4 = StrictMath.sin(angle4);
            }
            sumCos += cos4;
            sumSin += sin4;
        }
        double meanCos = sumCos / samples.size();
        double meanSin = sumSin / samples.size();
        double magnitude2 = meanCos * meanCos + meanSin * meanSin;
        double corrected = StrictMath.max(0.0, magnitude2 - 1.0 / samples.size());
        return StrictMath.sqrt(corrected);
    }

    private static double seam(List<E001Sample> samples, double originX, double originY) {
        long[] lower = new long[4];
        long[] upper = new long[4];
        for (E001Sample sample : samples) {
            double x = sample.x() - originX;
            double y = sample.y() - originY;
            for (int index = 0; index < 4; index++) {
                double normal = index < 2 ? x : y;
                double tangent = index < 2 ? y : x;
                double boundary = index % 2 == 0 ? -150.0 : 150.0;
                if (tangent >= -450.0 && tangent < 450.0) {
                    if (normal >= boundary - 15.0 && normal < boundary) {
                        lower[index]++;
                    } else if (normal >= boundary && normal < boundary + 15.0) {
                        upper[index]++;
                    }
                }
            }
        }
        double sum = 0.0;
        for (int index = 0; index < 4; index++) {
            double left = lower[index] + 0.5;
            double right = upper[index] + 0.5;
            sum += 2.0 * StrictMath.abs(left - right) / (left + right);
        }
        return sum / 4.0;
    }
}
