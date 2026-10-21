package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

public final class MapCacheMetrics {

    private final MeterRegistry meters;
    private final String cache;

    public MapCacheMetrics(MeterRegistry meters, String cache) {
        this.meters = meters;
        this.cache = cache;
    }

    public void request(String result) {
        meters.counter("pheeeew.map.cache.requests", "cache", cache, "result", result).increment();
    }

    public void rebuilt() {
        meters.counter("pheeeew.map.cache.rebuilds", "cache", cache).increment();
    }

    public void failed() {
        meters.counter("pheeeew.map.cache.failures", "cache", cache).increment();
    }

    public <T> void candidates(T source, ToDoubleFunction<T> size) {
        Gauge.builder("pheeeew.map.cache.candidates", source, size).tag("cache", cache).register(meters);
    }

    public <T> void pendingAge(T source, ToDoubleFunction<T> ageSeconds) {
        Gauge.builder("pheeeew.map.cache.pending.age", source, ageSeconds)
                .tag("cache", cache).baseUnit("seconds").register(meters);
    }

    /** 차단 SQL과 정렬은 제외하고 후보 선택부터 불변 결과 구성까지 측정한다. 실패한 선택은 기록하지 않는다. */
    public <T> T measurePinSearch(Supplier<T> search, ToIntFunction<T> scanned) {
        long started = System.nanoTime();
        T selection = search.get();

        meters.timer("pheeeew.map.cache.pin.search").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        meters.summary("pheeeew.map.cache.pin.scanned").record(scanned.applyAsInt(selection));

        return selection;
    }

    public <T> T measureRebuild(Supplier<T> rebuild) {
        Timer.Sample sample = Timer.start(meters);

        try {
            return rebuild.get();
        } finally {
            sample.stop(meters.timer("pheeeew.map.cache.rebuild", "cache", cache));
        }
    }
}
