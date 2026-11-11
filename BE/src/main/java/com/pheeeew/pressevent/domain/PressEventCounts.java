package com.pheeeew.pressevent.domain;

import static com.pheeeew.pressevent.exception.PressEventErrorCode.INVALID_COUNTS;

import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.emotion.domain.EmotionState;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class PressEventCounts {

    public static final int MAX_TOTAL = 400;

    private static final Set<EmotionState> REQUIRED_STATES = Set.of(EmotionState.values());

    private final Map<EmotionState, Integer> counts;
    private final int total;

    private PressEventCounts(Map<EmotionState, Integer> counts, int total) {
        this.counts = Map.copyOf(counts);
        this.total = total;
    }

    public static PressEventCounts from(Map<EmotionState, Integer> counts) {
        if (counts == null || !counts.keySet().equals(REQUIRED_STATES)
                || counts.values().stream().anyMatch(value -> Objects.isNull(value) || value < 0)) {
            throw new PheeeewException(INVALID_COUNTS, null);
        }
        long total = counts.values().stream().mapToLong(Integer::longValue).sum();
        if (total > MAX_TOTAL) {
            throw new PheeeewException(INVALID_COUNTS, null);
        }
        return new PressEventCounts(counts, (int) total);
    }

    public String normalized() {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(EmotionState::name)))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
