package com.pheeeew.emotion.presentation.config;

import com.pheeeew.emotion.infra.metrics.MapReadDiagnostics;
import com.pheeeew.emotion.infra.metrics.MapReadMetrics;
import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import com.pheeeew.emotion.infra.metrics.MapReadObservationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MapReadObservationConfiguration {

    @Bean
    MapReadObservationFilter mapReadObservationFilter(MeterRegistry registry, MapReadMetrics metrics, Environment environment,
            @Value("${pheeeew.emotion-map.diagnostics.enabled:false}") boolean enabled,
            @Value("${pheeeew.emotion-map.diagnostics.sample-rate:0.01}") double sampleRate,
            @Value("${pheeeew.emotion-map.diagnostics.slow-millis:100}") long slowMillis,
            @Value("${pheeeew.emotion-map.diagnostics.maximum-per-second:5}") int maximumPerSecond) {
        // dev와 prod가 동시에 활성인 경우에도 운영 표본 전송을 허용하지 않는다.
        boolean developmentOnly = environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("prod"));
        return new MapReadObservationFilter(registry, metrics,
                new MapReadDiagnostics(enabled && developmentOnly, sampleRate, slowMillis, maximumPerSecond, registry));
    }

    @Bean
    WebMvcConfigurer mapReadObservationMvc() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        MapReadObservation.mvcStarted();
                        return true;
                    }

                    @Override
                    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
                        MapReadObservation.mvcEnded();
                    }
                }).addPathPatterns("/api/v2/emotions/map", "/api/v2/emotions/map/regions",
                        "/api/v4/emotions/map", "/api/v4/emotions/map/regions");
            }
        };
    }
}
