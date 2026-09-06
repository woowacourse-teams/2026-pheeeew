package com.pheeeew.sigh.experiment.e001;

import java.util.Arrays;

final class E001Statistics {

    private E001Statistics() {
    }

    static double quantile(double[] values, double probability) {
        if (!(probability > 0.0 && probability <= 1.0)) {
            throw new IllegalArgumentException("quantile 확률은 (0, 1]이어야 해요.");
        }
        double[] sorted = sortedFinite(values);
        if (sorted.length == 0) {
            return Double.NaN;
        }
        return sorted[(int) StrictMath.ceil(probability * sorted.length) - 1];
    }

    static double median(double[] values) {
        double[] sorted = sortedFinite(values);
        int size = sorted.length;
        if (size == 0) {
            return Double.NaN;
        }
        if (size % 2 == 1) {
            return sorted[size / 2];
        }
        return (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0;
    }

    static double populationStd(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return Double.NaN;
            }
            sum += value;
        }
        double mean = sum / values.length;
        double squareSum = 0.0;
        for (double value : values) {
            double delta = value - mean;
            squareSum += delta * delta;
        }
        double variance = squareSum / values.length;
        return StrictMath.sqrt(StrictMath.max(0.0, variance));
    }

    private static double[] sortedFinite(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return new double[0];
            }
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted;
    }
}
