package com.pheeeew.sigh.experiment.e001;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class E001Integrity {

    private E001Integrity() {
    }

    static boolean passes(Map<Shard, Long> requested, List<E001Sample> samples,
                          long samplerFailureCount, boolean square) {
        if (samplerFailureCount != 0 || requested.isEmpty()
                || requested.values().stream().anyMatch(count -> count <= 0)) {
            return false;
        }
        Map<Shard, Set<Long>> generated = new HashMap<>();
        for (E001Sample sample : samples) {
            Shard shard = Shard.of(sample.sampleSeed(), sample.centerId());
            Long count = requested.get(shard);
            if (count == null || sample.pointIndex() < 0 || sample.pointIndex() >= count
                    || !E001ShapeMetrics.valid(sample, square)
                    || !generated.computeIfAbsent(shard, ignored -> new HashSet<>()).add(sample.pointIndex())) {
                return false;
            }
        }
        return requested.entrySet().stream().allMatch(entry ->
                generated.getOrDefault(entry.getKey(), Set.of()).size() == entry.getValue());
    }

    record Shard(long sampleSeed, String centerId) {

        static Shard of(long sampleSeed, String centerId) {
            return new Shard(sampleSeed, centerId);
        }
    }
}
