package com.pheeeew.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.EmotionPressMetrics.QueryOperation;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics.Operation;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlloyMetricContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"config.alloy", "config.dev.alloy"})
    void 지역과_개인_프레스의_실제_Prometheus_출력이_Alloy_허용_목록을_통과한다(String config) throws Exception {
        // given
        MockClock clock = new MockClock();
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), clock);
        try {
            RegionQueryMetrics regions = new RegionQueryMetrics(registry);
            EmotionPressMetrics press = new EmotionPressMetrics(registry);
            for (boolean succeeded : new boolean[] {true, false}) {
                for (Operation operation : Operation.values()) {
                    regions.recordQuery(operation, TimeUnit.SECONDS.toNanos(231), succeeded);
                }
                for (QueryOperation operation : QueryOperation.values()) {
                    var sample = press.startQuery();
                    clock.addSeconds(231);
                    press.recordQuery(sample, operation, succeeded);
                }
            }
            var matcher = Pattern.compile("regex\\s*=\\s*\"([^\"]+)\"")
                    .matcher(Files.readString(Path.of("docker/alloy", config)));
            assertThat(matcher.find()).isTrue();
            Pattern allowed = Pattern.compile(matcher.group(1));

            // when
            Set<String> names = registry.scrape().lines()
                    .filter(line -> !line.startsWith("#") && (line.startsWith("pheeeew_region_query_")
                            || line.startsWith("pheeeew_emotion_press_query_")))
                    .map(line -> line.substring(0, line.indexOf('{')))
                    .collect(Collectors.toSet());

            // then
            assertThat(names).containsExactlyInAnyOrder(
                    "pheeeew_region_query_seconds_count", "pheeeew_region_query_seconds_sum",
                    "pheeeew_region_query_seconds_bucket", "pheeeew_region_query_seconds_max",
                    "pheeeew_emotion_press_query_seconds_count", "pheeeew_emotion_press_query_seconds_sum",
                    "pheeeew_emotion_press_query_seconds_bucket", "pheeeew_emotion_press_query_seconds_max");
            assertThat(names).allSatisfy(name -> assertThat(allowed.matcher(name).matches()).isTrue());
        } finally {
            registry.close();
        }
    }
}
