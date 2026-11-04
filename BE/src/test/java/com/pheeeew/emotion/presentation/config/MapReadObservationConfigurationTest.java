package com.pheeeew.emotion.presentation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.infra.metrics.MapReadMetrics;
import com.pheeeew.emotion.infra.metrics.MapReadObservationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MapReadObservationConfigurationTest {

    @ParameterizedTest
    @CsvSource({"dev,true,true", "dev,false,false", "prod,true,false", "dev+prod,true,false", "test,true,false"})
    void 개발_전용_옵션을_명시한_경우에만_표본을_남긴다(String profiles, boolean enabled, boolean expected) throws Exception {
        // given
        try (var context = new AnnotationConfigApplicationContext()) {
            var meters = new SimpleMeterRegistry();
            context.getEnvironment().setActiveProfiles(profiles.split("\\+"));
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test", java.util.Map.of(
                    "pheeeew.emotion-map.diagnostics.enabled", enabled,
                    "pheeeew.emotion-map.diagnostics.slow-millis", 0)));
            context.registerBean(MeterRegistry.class, () -> meters);
            context.registerBean(MapReadMetrics.class, () -> new MapReadMetrics(meters));
            context.register(MapReadObservationConfiguration.class);
            context.refresh();

            // when
            context.getBean(MapReadObservationFilter.class).doFilter(new MockHttpServletRequest("GET", "/api/v4/emotions/map"),
                    new MockHttpServletResponse(), (request, response) -> { });

            // then
            double count = meters.find("pheeeew.map.diagnostic.logs").tag("result", "sampled").counters().stream()
                    .mapToDouble(counter -> counter.count()).sum();
            assertThat(count).isEqualTo(expected ? 1 : 0);
        }
    }
}
