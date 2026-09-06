package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class E001Allocation {

    private E001Allocation() {
    }

    static Map<String, Long> distribute(Map<String, Double> weights, long total) {
        if (weights.isEmpty() || total < 0 || total > Integer.MAX_VALUE
                || weights.values().stream().anyMatch(value -> !Double.isFinite(value) || value <= 0.0)) {
            throw new IllegalArgumentException("배분에는 유한한 양의 가중치와 유효한 표본 개수가 필요해요.");
        }
        List<String> ids = weights.keySet().stream().sorted().toList();
        double sum = 0.0;
        for (String id : ids) {
            sum += weights.get(id);
        }
        if (!Double.isFinite(sum)) {
            throw new IllegalArgumentException("가중치 합은 유한해야 해요.");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        long allocated = 0;
        for (String id : ids) {
            double expected = (total * weights.get(id)) / sum;
            long base = (long) StrictMath.floor(expected);
            counts.put(id, base);
            remainders.put(id, expected - base);
            allocated += base;
        }
        long remaining = total - allocated;
        if (remaining < 0 || remaining > ids.size()) {
            throw new IllegalStateException("largest-remainder 배분의 잔여 개수가 유효하지 않아요.");
        }
        List<String> ranked = new ArrayList<>(ids);
        ranked.sort(Comparator.<String>comparingDouble(remainders::get).reversed().thenComparing(id -> id));
        for (int index = 0; index < remaining; index++) {
            String id = ranked.get(index);
            counts.put(id, counts.get(id) + 1);
        }
        return java.util.Collections.unmodifiableMap(counts);
    }
}
