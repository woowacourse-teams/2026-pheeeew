package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class E001DensityMetrics {

    private static final double SEARCH_CELL_METERS = 24.0;

    private E001DensityMetrics() {
    }

    static E001MetricSet measure(List<E001Sample> pool, double originX, double originY, int halfWidth) {
        if (halfWidth != 300 && halfWidth != 600 && halfWidth != 1_350) {
            throw new IllegalArgumentException("평가 창의 반폭은 300, 600, 1350미터 중 하나여야 해요.");
        }
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || originX % 50.0 != 0.0 || originY % 50.0 != 0.0) {
            throw new IllegalArgumentException("평가 창은 절대 50미터 cell 경계에 맞아야 해요.");
        }
        if (pool.stream().anyMatch(p -> !Double.isFinite(p.x()) || !Double.isFinite(p.y()))) {
            return result(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        List<E001Sample> ordered = pool.stream().sorted(E001Sample.ORDER).toList();
        List<E001Sample> targets = ordered.stream().filter(p ->
                p.x() >= originX - halfWidth && p.x() < originX + halfWidth
                        && p.y() >= originY - halfWidth && p.y() < originY + halfWidth).toList();
        Map<Cell, List<E001Sample>> cells = index(ordered);
        long within8 = 0;
        long within16 = 0;
        long within24 = 0;
        double neighborSum = 0.0;
        for (E001Sample target : targets) {
            double nearest = Double.POSITIVE_INFINITY;
            int neighbors = 0;
            for (E001Sample other : nearby(cells, target)) {
                if (!target.samePoint(other)) {
                    double distance = StrictMath.hypot(target.x() - other.x(), target.y() - other.y());
                    nearest = StrictMath.min(nearest, distance);
                    if (distance < 10.0) {
                        neighbors++;
                    }
                }
            }
            if (nearest < 8.0) {
                within8++;
            }
            if (nearest < 16.0) {
                within16++;
            }
            if (nearest < 24.0) {
                within24++;
            }
            neighborSum += neighbors;
        }

        double[] counts = hotspotCounts(targets, originX, originY, halfWidth);
        double sum = 0.0;
        for (double count : counts) {
            sum += count;
        }
        double mean = sum / counts.length;
        double cv = mean == 0.0 ? Double.NaN : E001Statistics.populationStd(counts) / mean;
        double size = targets.size();
        return result(within8 / size, within16 / size, within24 / size, neighborSum / size,
                E001Statistics.quantile(counts, 0.99), E001Statistics.quantile(counts, 1.0), cv);
    }

    private static E001MetricSet result(double p8, double p16, double p24, double neighbors,
                                        double p99, double max, double cv) {
        return E001MetricSet.from(Map.of("proximity8", p8, "proximity16", p16, "proximity24", p24,
                "neighbors10", neighbors, "hotspot50.p99", p99, "hotspot50.max", max, "hotspot50.cv", cv));
    }

    private static Map<Cell, List<E001Sample>> index(List<E001Sample> pool) {
        Map<Cell, List<E001Sample>> cells = new HashMap<>();
        for (E001Sample point : pool) {
            Cell cell = Cell.of((long) StrictMath.floor(point.x() / SEARCH_CELL_METERS),
                    (long) StrictMath.floor(point.y() / SEARCH_CELL_METERS));
            cells.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(point);
        }
        return cells;
    }

    private static List<E001Sample> nearby(Map<Cell, List<E001Sample>> cells, E001Sample target) {
        long x = (long) StrictMath.floor(target.x() / SEARCH_CELL_METERS);
        long y = (long) StrictMath.floor(target.y() / SEARCH_CELL_METERS);
        List<E001Sample> neighbors = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                neighbors.addAll(cells.getOrDefault(Cell.of(x + dx, y + dy), List.of()));
            }
        }
        neighbors.sort(E001Sample.ORDER);
        return neighbors;
    }

    private static double[] hotspotCounts(List<E001Sample> targets, double originX, double originY, int halfWidth) {
        int width = halfWidth * 2 / 50;
        long minCellX = (long) StrictMath.floor((originX - halfWidth) / 50.0);
        long minCellY = (long) StrictMath.floor((originY - halfWidth) / 50.0);
        double[] counts = new double[width * width];
        for (E001Sample target : targets) {
            int x = (int) ((long) StrictMath.floor(target.x() / 50.0) - minCellX);
            int y = (int) ((long) StrictMath.floor(target.y() / 50.0) - minCellY);
            counts[y * width + x]++;
        }
        return counts;
    }

    private record Cell(long x, long y) {

        static Cell of(long x, long y) {
            return new Cell(x, y);
        }
    }
}
