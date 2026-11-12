package com.pheeeew.climb.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 키마다 고정 창 안에서 limit 번까지 허용한다. 서버 메모리에만 둔다. */
public class ClimbRateLimit {

    private static final int PRUNE_AT = 10_000;

    private final int limit;
    private final Duration window;
    private final Map<String, Window> windows = new HashMap<>();

    public ClimbRateLimit(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public synchronized boolean tryAcquire(String key, Instant now) {
        if (windows.size() >= PRUNE_AT) {
            windows.values().removeIf(entry -> !now.isBefore(entry.startedAt.plus(window)));
        }
        Window current = windows.get(key);
        if (current == null || !now.isBefore(current.startedAt.plus(window))) {
            current = new Window(now);
            windows.put(key, current);
        }
        if (current.count >= limit) {
            return false;
        }
        current.count++;
        return true;
    }

    private static final class Window {

        private final Instant startedAt;
        private int count;

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
