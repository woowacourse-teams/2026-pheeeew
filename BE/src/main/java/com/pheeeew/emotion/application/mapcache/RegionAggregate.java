package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RegionAggregate {

    private static final List<EmotionState> PRIORITY = List.of(
            EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
            EmotionState.FRUSTRATED, EmotionState.IRRITATED);

    private final Map<String, Map<EmotionState, Long>> counts;
    private final Instant nextActivation;

    private RegionAggregate(Map<String, Map<EmotionState, Long>> counts, Instant nextActivation) {
        this.counts = counts;
        this.nextActivation = nextActivation;
    }

    static RegionAggregate from(List<RegionEmotionStateCount> rows) {
        Map<String, Map<EmotionState, Long>> counts = new HashMap<>();
        Instant nextActivation = null;

        for (RegionEmotionStateCount row : rows) {
            if (row.amount() > 0) {
                counts.computeIfAbsent(row.code(), ignored -> new HashMap<>()).put(row.state(), row.amount());
            }
            if (row.nextActivation() != null && (nextActivation == null || row.nextActivation().isBefore(nextActivation))) {
                nextActivation = row.nextActivation();
            }
        }

        return new RegionAggregate(immutableCounts(counts), nextActivation);
    }

    RegionAggregate applyChange(EmotionMapContribution before, EmotionMapContribution after, Instant now) {
        Map<String, Map<EmotionState, Long>> updated = new HashMap<>();
        counts.forEach((code, values) -> updated.put(code, new HashMap<>(values)));

        adjust(updated, before, -1, now);
        adjust(updated, after, 1, now);

        Instant nextActivation = this.nextActivation;
        if (after != null && !after.deleted() && after.hasContent() && after.createdAt().isAfter(now)
                && (nextActivation == null || after.createdAt().isBefore(nextActivation))) {
            nextActivation = after.createdAt();
        }

        return new RegionAggregate(immutableCounts(updated), nextActivation);
    }

    Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries() {
        Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries = new EnumMap<>(RegionLevel.class);
        summaries.put(RegionLevel.SIGUNGU, summariesOf(counts));

        Map<String, Map<EmotionState, Long>> sido = new HashMap<>();
        counts.forEach((code, values) -> values.forEach((state, amount) ->
                sido.computeIfAbsent(code.substring(0, 2), ignored -> new HashMap<>()).merge(state, amount, Long::sum)));
        summaries.put(RegionLevel.SIDO, summariesOf(sido));

        return Map.copyOf(summaries);
    }

    Instant nextActivation() {
        return nextActivation;
    }

    private static Map<String, Map<EmotionState, Long>> immutableCounts(Map<String, Map<EmotionState, Long>> counts) {
        Map<String, Map<EmotionState, Long>> result = new HashMap<>();
        counts.forEach((code, values) -> result.put(code, Collections.unmodifiableMap(new HashMap<>(values))));

        return Map.copyOf(result);
    }

    private static void adjust(Map<String, Map<EmotionState, Long>> counts, EmotionMapContribution entry, long delta, Instant now) {
        if (entry == null || !entry.contributesAt(now)) {
            return;
        }

        String code = entry.regionCode().substring(0, 5);
        Map<EmotionState, Long> values = counts.computeIfAbsent(code, ignored -> new HashMap<>());
        long updated = values.getOrDefault(entry.state(), 0L) + delta;

        if (updated < 0) {
            throw new IllegalStateException("지역 집계 기여분이 음수입니다.");
        }

        if (updated == 0) {
            values.remove(entry.state());
            if (values.isEmpty()) {
                counts.remove(code);
            }
        } else {
            values.put(entry.state(), updated);
        }
    }

    private static Map<String, RegionEmotionSummary> summariesOf(Map<String, Map<EmotionState, Long>> counts) {
        Map<String, RegionEmotionSummary> summaries = new HashMap<>();
        counts.forEach((code, values) -> {
            long total = values.values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                EmotionState representative = null;
                long highest = 0;
                for (EmotionState state : PRIORITY) {
                    long amount = values.getOrDefault(state, 0L);
                    if (amount > highest) {
                        representative = state;
                        highest = amount;
                    }
                }

                summaries.put(code, RegionEmotionSummary.of(total, representative));
            }
        });

        return Map.copyOf(summaries);
    }

}
