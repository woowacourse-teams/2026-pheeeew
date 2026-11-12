package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;

/** 복구·보정 실행을 결과(success·busy·failed)별 한 타이머로 기록한다. */
public final class MapCacheMaintenanceMetrics {

    private final MeterRegistry meters;

    public MapCacheMaintenanceMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    public long startRun() {
        return System.nanoTime();
    }

    /** completed가 null이면 쓰기 잠금을 얻지 못한 실행(busy)이다. */
    public void recordRun(long started, Boolean completed) {
        record(completed == null ? "busy" : completed ? "success" : "failed", System.nanoTime() - started);
    }

    /** 실행기가 예약을 거절해 실행 자체가 없었다. */
    public void rejected() {
        record("failed", 0);
    }

    private void record(String result, long nanos) {
        meters.timer("pheeeew.map.cache.maintenance", "result", result).record(nanos, TimeUnit.NANOSECONDS);
    }
}
