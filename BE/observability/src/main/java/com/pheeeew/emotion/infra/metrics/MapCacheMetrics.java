package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    /** 캐시 결과(hit·miss·fallback)를 요청의 지도 조회 결정으로 넘긴다. 집계는 pheeeew.map.read.requests가 한다. */
    public void request(String result) {
        try {
            MapReadObservation.cacheResult(cache, result);
        } catch (RuntimeException ignored) {
            // 요청별 진단이 기존 캐시 조회에 영향을 주지 않게 한다.
        }
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

    /** 핀 선택에서 검사한 후보 수를 기록한다. 탐색 시간은 요청 단계 map_pins_cache에 포함된다. 실패한 선택은 기록하지 않는다. */
    public <T> T measurePinSearch(Supplier<T> search, ToIntFunction<T> scanned) {
        T selection = search.get();
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
