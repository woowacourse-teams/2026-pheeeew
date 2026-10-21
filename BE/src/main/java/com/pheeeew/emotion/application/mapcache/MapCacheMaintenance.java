package com.pheeeew.emotion.application.mapcache;

import io.micrometer.core.instrument.MeterRegistry;
import com.pheeeew.emotion.infra.metrics.MapCacheMaintenanceMetrics;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;

/** 시작·보정·복구 작업은 하나로 합치고 실패 재시도 횟수를 제한한다. */
@Component
public class MapCacheMaintenance implements AutoCloseable {

    private static final Duration RECONCILE_INTERVAL = Duration.ofMinutes(10);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30));

    private final boolean automaticStart;
    private final MapCacheUpdateScheduler updates;
    private final EmotionMapCacheCoordinator coordinator;
    private final ObjectProvider<EmotionMapCacheListener> listeners;
    private final MapCacheMaintenanceMetrics metrics;
    private final Object state = new Object();
    private Work pending;
    private ScheduledFuture<?> periodic;
    private boolean started;
    private boolean closed;

    public MapCacheMaintenance(MapCacheUpdateScheduler updates, EmotionMapCacheCoordinator coordinator,
            ObjectProvider<EmotionMapCacheListener> listeners, MeterRegistry meters,
            @Value("${pheeeew.map-cache.maintenance.automatic-start:true}") boolean automaticStart) {
        this.automaticStart = automaticStart;
        this.updates = updates;
        this.coordinator = coordinator;
        this.listeners = listeners;
        this.metrics = new MapCacheMaintenanceMetrics(meters);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (automaticStart) {
            start();
        }
    }

    public void start() {
        synchronized (state) {
            if (closed || started) {
                return;
            }

            started = true;
            requestRebuild();
            schedulePeriodic();
        }
    }

    public void requestRebuild() {
        synchronized (state) {
            if (closed || !updates.available()) {
                return;
            }
            if (pending != null && pending.attempt == 0) {
                return;
            }

            cancelPending();
            schedule(0, Duration.ZERO);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        synchronized (state) {
            closed = true;

            cancelPending();
            if (periodic != null) {
                periodic.cancel(false);
            }
        }
    }

    private void schedule(int attempt, Duration delay) {
        Work work = new Work(attempt);
        pending = work;
        work.future = updates.schedule(() -> run(work), delay);

        if (work.future == null) {
            pending = null;
            metrics.failed();
        }
    }

    private void run(Work work) {
        synchronized (state) {
            if (closed || pending != work) {
                return;
            }

            pending = null;
        }

        Boolean result = Boolean.FALSE;
        long began = metrics.startRun();

        try {
            result = coordinator.trySerialized(() -> {
                boolean complete = true;

                for (EmotionMapCacheListener listener : listeners.orderedStream().toList()) {
                    try {
                        complete &= listener.rebuild();
                    } catch (RuntimeException failure) {
                        complete = false;
                    }
                }

                return complete;
            });
        } catch (RuntimeException failure) {
            // 원본 쓰기는 이미 완료됐다. 복구 실패는 SQL 응답과 제한된 재시도로 처리한다.
        } finally {
            metrics.recordRun(began);
        }

        synchronized (state) {
            if (!Boolean.TRUE.equals(result)) {
                if (result == null) {
                    metrics.busy();
                } else {
                    metrics.failed();
                }
                if (!closed && pending == null && work.attempt < RETRY_DELAYS.size()) {
                    schedule(work.attempt + 1, RETRY_DELAYS.get(work.attempt));
                }
            }

            if (started && !closed && periodic == null) {
                schedulePeriodic();
            }
        }
    }

    private void schedulePeriodic() {
        periodic = updates.schedule(() -> {
            synchronized (state) {
                periodic = null;
                if (!closed) {
                    requestRebuild();
                }
            }
        }, RECONCILE_INTERVAL);
    }

    private void cancelPending() {
        if (pending != null && pending.future != null) {
            pending.future.cancel(false);
        }
        pending = null;
    }

    private static final class Work {

        private final int attempt;
        private ScheduledFuture<?> future;

        private Work(int attempt) {
            this.attempt = attempt;
        }
    }
}
