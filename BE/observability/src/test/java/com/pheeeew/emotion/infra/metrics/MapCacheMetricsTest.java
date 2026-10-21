package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapCacheMetricsTest {

    @Test
    void 캐시별_요청_결과와_재생성_성공실패를_기존_이름과_태그로_기록한다() {
        // given
        var meters = new SimpleMeterRegistry();
        var pins = new MapCacheMetrics(meters, "pins");
        var regions = new MapCacheMetrics(meters, "regions");

        // when
        pins.request("hit");
        pins.request("miss");
        regions.request("fallback");
        pins.rebuilt();
        regions.failed();

        // then
        assertThat(meters.get("pheeeew.map.cache.requests").tags("cache", "pins", "result", "hit").counter().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.requests").tags("cache", "pins", "result", "miss").counter().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.requests").tags("cache", "regions", "result", "fallback").counter().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.rebuilds").tag("cache", "pins").counter().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.failures").tag("cache", "regions").counter().count()).isEqualTo(1);
    }

    @Test
    void 후보_수는_등록_시점의_값이_아니라_현재_값을_읽는다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheMetrics(meters, "pins");
        var size = new AtomicInteger(10);
        metrics.candidates(size, AtomicInteger::get);

        // when
        size.set(20);

        // then
        assertThat(meters.get("pheeeew.map.cache.candidates").tag("cache", "pins").gauge().value()).isEqualTo(20);
    }

    @Test
    void 미게시_경과시간은_캐시별로_현재_초단위_값을_읽는다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheMetrics(meters, "pins");
        var millis = new AtomicInteger(500);
        metrics.pendingAge(millis, source -> source.get() / 1000.0);
        var gauge = meters.get("pheeeew.map.cache.pending.age").tag("cache", "pins").gauge();

        // when
        double pending = gauge.value();
        millis.set(0);

        // then
        assertThat(pending).isEqualTo(0.5);
        assertThat(gauge.value()).isZero();
        assertThat(gauge.getId().getBaseUnit()).isEqualTo("seconds");
    }

    @Test
    void 핀_선택_결과는_그대로_반환하고_성공한_선택만_시간과_검사수를_기록한다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheMetrics(meters, "pins");
        var selection = List.of(1, 2, 3);
        var failure = new IllegalStateException("선택 실패");

        // when
        var result = metrics.measurePinSearch(() -> selection, List::size);

        // then
        assertThat(result).isSameAs(selection);
        assertThatThrownBy(() -> metrics.<List<Integer>>measurePinSearch(() -> {
            throw failure;
        }, List::size)).isSameAs(failure);
        assertThat(meters.get("pheeeew.map.cache.pin.search").timer().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.pin.scanned").summary().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.pin.scanned").summary().totalAmount()).isEqualTo(3);
    }

    @Test
    void 재생성은_null_반환과_실패에도_소요시간을_기록하고_예외를_그대로_전달한다() {
        // given
        var clock = new MockClock();
        var meters = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        var metrics = new MapCacheMetrics(meters, "regions");
        var failure = new IllegalStateException("재생성 실패");

        // when
        Object result = metrics.measureRebuild(() -> {
            clock.add(Duration.ofMillis(10));
            return null;
        });

        // then
        assertThat(result).isNull();
        assertThatThrownBy(() -> metrics.measureRebuild(() -> {
            clock.add(Duration.ofMillis(20));
            throw failure;
        })).isSameAs(failure);
        assertThat(meters.get("pheeeew.map.cache.rebuild").tag("cache", "regions").timer().count()).isEqualTo(2);
        assertThat(meters.get("pheeeew.map.cache.rebuild").tag("cache", "regions").timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30);
    }
}
