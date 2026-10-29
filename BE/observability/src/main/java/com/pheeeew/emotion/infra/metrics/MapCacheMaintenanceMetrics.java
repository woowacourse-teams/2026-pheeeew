package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public final class MapCacheMaintenanceMetrics {

    private final MeterRegistry meters;
    private final Timer runTime;

    public MapCacheMaintenanceMetrics(MeterRegistry meters) {
        this.meters = meters;
        runTime = meters.timer("pheeeew.map.cache.maintenance");
    }

    public long startRun() {
        return System.nanoTime();
    }

    public void recordRun(long started) {
        runTime.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }

    public void busy() {
        meters.counter("pheeeew.map.cache.maintenance.busy").increment();
    }

    public void failed() {
        meters.counter("pheeeew.map.cache.maintenance.failures").increment();
    }
}
