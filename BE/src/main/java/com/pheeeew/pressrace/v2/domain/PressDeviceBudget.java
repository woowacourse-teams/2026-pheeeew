package com.pheeeew.pressrace.v2.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;

public class PressDeviceBudget {

    private static final double CAPACITY = 100;
    private static final double REFILL_PER_SECOND = 20;

    private final long deviceId;
    private double tokens;
    private Instant refilledAt;

    @Builder
    private PressDeviceBudget(long deviceId, Instant refilledAt) {
        this.deviceId = deviceId;
        this.tokens = CAPACITY;
        this.refilledAt = refilledAt;
    }

    public long deviceId() {
        return deviceId;
    }

    public boolean consume(int count, Instant now) {
        double elapsed = Math.max(0, Duration.between(refilledAt, now).toNanos() / 1e9);
        double available = Math.min(CAPACITY, tokens + elapsed * REFILL_PER_SECOND);
        if (available < count) {
            return false;
        }
        tokens = available - count;
        refilledAt = now;
        return true;
    }
}
