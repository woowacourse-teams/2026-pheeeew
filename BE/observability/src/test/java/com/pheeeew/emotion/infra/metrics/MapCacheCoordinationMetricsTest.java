package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MapCacheCoordinationMetricsTest {

    @Test
    void 잠금_대기_타이머는_미리_등록하고_대기_기록만_누적한다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheLockMetrics(meters);
        var timer = meters.get("pheeeew.map.cache.lock.wait").timer();
        assertThat(timer.count()).isZero();

        // when
        long started = metrics.startWait();
        assertThat(timer.count()).isZero();
        metrics.recordWait(started);

        // then
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTags()).isEmpty();
        assertThat(meters.find("pheeeew.map.cache.maintenance").timer()).isNull();
    }

    @Test
    void 보정_타이머는_미리_등록하고_busy와_실패는_발생할_때만_별도로_누적한다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheMaintenanceMetrics(meters);
        var timer = meters.get("pheeeew.map.cache.maintenance").timer();
        assertThat(timer.count()).isZero();
        assertThat(meters.find("pheeeew.map.cache.maintenance.busy").counter()).isNull();
        assertThat(meters.find("pheeeew.map.cache.maintenance.failures").counter()).isNull();

        // when
        long started = metrics.startRun();
        metrics.busy();
        metrics.failed();
        metrics.failed();
        metrics.recordRun(started);

        // then
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTags()).isEmpty();
        assertThat(meters.get("pheeeew.map.cache.maintenance.busy").counter().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.maintenance.failures").counter().count()).isEqualTo(2);
        assertThat(meters.find("pheeeew.map.cache.lock.wait").timer()).isNull();
    }
}
