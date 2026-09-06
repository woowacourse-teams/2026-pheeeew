package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class E001SpectralMetrics {

    private static final int SIZE = 180;
    private static final double TWO_PI = 0x1.921fb54442d18p2;

    private E001SpectralMetrics() {
    }

    static double grid300(List<E001Sample> samples, double originX, double originY) {
        if (samples.stream().anyMatch(p -> !Double.isFinite(p.x()) || !Double.isFinite(p.y()))) {
            return Double.NaN;
        }
        return grid300(raster(samples, originX, originY));
    }

    static long[][] raster(List<E001Sample> samples, double originX, double originY) {
        long[][] counts = new long[SIZE][SIZE];
        for (E001Sample sample : samples) {
            double x = sample.x() - originX;
            double y = sample.y() - originY;
            if (x >= -1_350.0 && x < 1_350.0 && y >= -1_350.0 && y < 1_350.0) {
                int ix = (int) StrictMath.floor((x + 1_350.0) / 15.0);
                int iy = (int) StrictMath.floor((y + 1_350.0) / 15.0);
                counts[iy][ix]++;
            }
        }
        return counts;
    }

    static double grid300(long[][] counts) {
        if (counts.length != SIZE || Arrays.stream(counts).anyMatch(row -> row.length != SIZE)) {
            throw new IllegalArgumentException("spectral raster는 180×180이어야 해요.");
        }
        double total = 0.0;
        for (int iy = 0; iy < SIZE; iy++) {
            for (int ix = 0; ix < SIZE; ix++) {
                if (counts[iy][ix] < 0) {
                    throw new IllegalArgumentException("raster count는 음수일 수 없어요.");
                }
                total += counts[iy][ix];
            }
        }
        if (total == 0.0) {
            return Double.NaN;
        }
        double[] hann = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            hann[i] = 0.5 * (1.0 - StrictMath.cos(TWO_PI * (i / 179.0)));
        }
        double mean = total / 32_400.0;
        double[][] windowed = new double[SIZE][SIZE];
        for (int iy = 0; iy < SIZE; iy++) {
            for (int ix = 0; ix < SIZE; ix++) {
                windowed[iy][ix] = ((counts[iy][ix] - mean) * hann[ix]) * hann[iy];
            }
        }
        double numerator = 0.0;
        for (Bin bin : List.of(Bin.of(9, 0), Bin.of(-9, 0), Bin.of(0, 9), Bin.of(0, -9))) {
            numerator += power(windowed, bin);
        }
        numerator /= 4.0;
        List<Bin> bins = denominatorBins();
        if (bins.size() != 144) {
            throw new IllegalStateException("grid300 분모 bin은 정확히 144개여야 해요.");
        }
        double[] powers = new double[bins.size()];
        for (int index = 0; index < bins.size(); index++) {
            powers[index] = power(windowed, bins.get(index));
        }
        Arrays.sort(powers);
        double denominator = (powers[71] + powers[72]) / 2.0;
        double ratio = numerator / denominator;
        if (denominator == 0.0 || !Double.isFinite(ratio)
                || Arrays.stream(powers).anyMatch(p -> !Double.isFinite(p))) {
            return Double.NaN;
        }
        return ratio;
    }

    static List<Bin> denominatorBins() {
        List<Bin> bins = new ArrayList<>();
        for (int ky = -90; ky <= 89; ky++) {
            for (int kx = -90; kx <= 89; kx++) {
                double radius = StrictMath.hypot(kx, ky);
                if (radius >= 7.2 && radius <= 10.8) {
                    double axisRatio = StrictMath.min(StrictMath.abs(kx), StrictMath.abs(ky)) / radius;
                    if (axisRatio > 0x1.0907dc1930690p-2) {
                        bins.add(Bin.of(kx, ky));
                    }
                }
            }
        }
        return List.copyOf(bins);
    }

    private static double power(double[][] windowed, Bin bin) {
        double real = 0.0;
        double imaginary = 0.0;
        for (int iy = 0; iy < SIZE; iy++) {
            for (int ix = 0; ix < SIZE; ix++) {
                double phaseX = (bin.x() * ix) / 180.0;
                double phaseY = (bin.y() * iy) / 180.0;
                double phase = -TWO_PI * (phaseX + phaseY);
                real += windowed[iy][ix] * StrictMath.cos(phase);
                imaginary += windowed[iy][ix] * StrictMath.sin(phase);
            }
        }
        return real * real + imaginary * imaginary;
    }

    record Bin(int x, int y) {

        static Bin of(int x, int y) {
            return new Bin(x, y);
        }
    }
}
