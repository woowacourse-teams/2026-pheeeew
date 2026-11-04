package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.infra.metrics.MapReadMetrics.Stage;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MapReadMetricsTest {

    @Test
    void 단계별_성공과_실패를_분리하고_작은_지연도_히스토그램으로_내보낸다() {
        // given
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var metrics = new MapReadMetrics(registry);

            // when
            metrics.record(Stage.MAP_BLOCKS_LOOKUP, TimeUnit.MILLISECONDS.toNanos(7), true);
            metrics.record(Stage.MAP_BLOCKS_LOOKUP, TimeUnit.MILLISECONDS.toNanos(30), false);

            // then
            var success = registry.get("pheeeew.map.read.stage")
                    .tags("stage", "map_blocks_lookup", "outcome", "success").timer();
            var error = registry.get("pheeeew.map.read.stage")
                    .tags("stage", "map_blocks_lookup", "outcome", "error").timer();
            assertThat(success.count()).isEqualTo(1);
            assertThat(success.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7);
            assertThat(error.count()).isEqualTo(1);
            assertThat(error.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30);
            assertThat(registry.scrape().lines().filter(line -> line.startsWith("pheeeew_map_read_stage_seconds_bucket{"))
                    .filter(line -> line.contains("stage=\"map_blocks_lookup\""))
                    .filter(line -> line.contains("outcome=\"success\""))
                    .filter(line -> line.contains("le=\"0.01\""))).singleElement().asString().endsWith(" 1");
            assertThat(success.getId().getTags()).hasSize(3);
            assertThat(success.getId().getTag("api_version")).isEqualTo("unscoped");
        } finally {
            registry.close();
        }
    }
}
