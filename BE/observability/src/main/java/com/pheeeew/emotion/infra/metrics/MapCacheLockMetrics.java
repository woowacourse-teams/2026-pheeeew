package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public final class MapCacheLockMetrics {

    private final Timer lockWait;

    public MapCacheLockMetrics(MeterRegistry meters) {
        lockWait = meters.timer("pheeeew.map.cache.lock.wait");
    }

    public long startWait() {
        return System.nanoTime();
    }

    public void recordWait(long started) {
        lockWait.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
}
