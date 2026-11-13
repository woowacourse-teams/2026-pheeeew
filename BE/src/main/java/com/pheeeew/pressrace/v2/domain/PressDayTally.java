package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import lombok.Builder;

public class PressDayTally {

    private final long deviceId;
    private final LocalDate date;
    private final Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
    private long total;
    private Instant lastAppliedAt;
    private boolean loaded;
    private long version;
    private long flushedVersion;

    @Builder
    private PressDayTally(long deviceId, LocalDate date) {
        this.deviceId = deviceId;
        this.date = date;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void load(Map<EmotionState, Long> stored) {
        stored.forEach((state, count) -> {
            if (count > 0) {
                counts.put(state, count);
                total += count;
            }
        });
        loaded = true;
    }

    public void add(Map<EmotionState, Integer> presses, Instant now) {
        presses.forEach((state, count) -> {
            if (count > 0) {
                counts.merge(state, count.longValue(), Long::sum);
                total += count;
            }
        });
        lastAppliedAt = now;
        version++;
    }

    public Map<EmotionState, Long> counts() {
        Map<EmotionState, Long> result = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            result.put(state, counts.getOrDefault(state, 0L));
        }
        return Map.copyOf(result);
    }

    public boolean isDirty() {
        return version > flushedVersion;
    }

    public PressDaySnapshot snapshot() {
        return PressDaySnapshot.of(deviceId, date, Map.copyOf(counts), total, lastAppliedAt, version);
    }

    public void markFlushed(long version) {
        flushedVersion = Math.max(flushedVersion, version);
    }
}
