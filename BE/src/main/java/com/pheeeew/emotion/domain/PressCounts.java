package com.pheeeew.emotion.domain;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

public record PressCounts(SequencedMap<EmotionState, Integer> presses, int perStateDropped, int totalDropped) {

    private static final int MAX_PER_STATE = 30;
    private static final int MAX_TOTAL = 100;

    public static PressCounts from(Map<EmotionState, Integer> counts) {
        List<EmotionState> ordered = counts.keySet().stream()
                .sorted(Comparator.comparing(EmotionState::name))
                .toList();

        SequencedMap<EmotionState, Integer> presses = new LinkedHashMap<>();
        int perStateDropped = 0;
        int totalDropped = 0;
        int appliedTotal = 0;
        for (EmotionState state : ordered) {
            int requested = counts.get(state);
            if (requested == 0) {
                continue;
            }

            int perState = Math.min(requested, MAX_PER_STATE);
            perStateDropped += requested - perState;
            int applied = Math.min(perState, MAX_TOTAL - appliedTotal);
            totalDropped += perState - applied;
            if (applied > 0) {
                presses.put(state, applied);
                appliedTotal += applied;
            }
        }

        return new PressCounts(Collections.unmodifiableSequencedMap(presses), perStateDropped, totalDropped);
    }

    public int appliedTotal() {
        return presses.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
}
