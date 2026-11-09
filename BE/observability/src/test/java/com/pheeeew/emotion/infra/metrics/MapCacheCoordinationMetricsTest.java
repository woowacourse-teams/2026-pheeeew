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
    void 보정_실행은_결과별_한_타이머로_기록하고_예약_거절은_실패로_센다() {
        // given
        var meters = new SimpleMeterRegistry();
        var metrics = new MapCacheMaintenanceMetrics(meters);

        // when
        metrics.recordRun(metrics.startRun(), Boolean.TRUE);
        metrics.recordRun(metrics.startRun(), null);
        metrics.recordRun(metrics.startRun(), Boolean.FALSE);
        metrics.rejected();

        // then
        assertThat(meters.get("pheeeew.map.cache.maintenance").tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.maintenance").tag("result", "busy").timer().count()).isEqualTo(1);
        assertThat(meters.get("pheeeew.map.cache.maintenance").tag("result", "failed").timer().count()).isEqualTo(2);
        assertThat(meters.find("pheeeew.map.cache.maintenance.busy").meters()).isEmpty();
        assertThat(meters.find("pheeeew.map.cache.maintenance.failures").meters()).isEmpty();
        assertThat(meters.find("pheeeew.map.cache.lock.wait").timer()).isNull();
    }
}
