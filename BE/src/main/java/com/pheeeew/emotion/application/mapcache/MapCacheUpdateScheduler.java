package com.pheeeew.emotion.application.mapcache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 조회용 갱신 실행 경계. 각 캐시가 미완료 예약을 하나로 합쳐 작업의 유입을 제한한다. */
public class MapCacheUpdateScheduler implements AutoCloseable {

    private final Clock clock;
    private final ScheduledExecutorService executor;
    private volatile boolean closed;

    public MapCacheUpdateScheduler(Clock clock, ScheduledExecutorService executor) {
        this.clock = clock;
        this.executor = executor;
    }

    public ScheduledFuture<?> schedule(Runnable update, Duration delay) {
        if (!available()) {
            return null;
        }

        try {
            return executor.schedule(update, Math.max(0, delay.toNanos()), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException rejected) {
            return null;
        }
    }

    public boolean available() {
        return !closed && !executor.isShutdown();
    }

    public Instant now() {
        return Instant.now(clock);
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
