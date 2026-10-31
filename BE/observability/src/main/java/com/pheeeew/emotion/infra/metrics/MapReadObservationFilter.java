package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.LongSupplier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** 기존 요청 로그 필터 안에서 동기 지도 GET만 감싼다. 비동기·오류 재디스패치는 관측하지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public final class MapReadObservationFilter extends OncePerRequestFilter {

    private final MeterRegistry registry;
    private final MapReadMetrics metrics;
    private final MapReadDiagnostics diagnostics;
    private final LongSupplier nanoTime;

    public MapReadObservationFilter(MeterRegistry registry, MapReadMetrics metrics, MapReadDiagnostics diagnostics) {
        this(registry, metrics, diagnostics, System::nanoTime);
    }

    MapReadObservationFilter(MeterRegistry registry, MapReadMetrics metrics, MapReadDiagnostics diagnostics, LongSupplier nanoTime) {
        this.registry = registry;
        this.metrics = metrics;
        this.diagnostics = diagnostics;
        this.nanoTime = nanoTime;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return MapReadObservation.route(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MapReadObservation observation;
        try {
            observation = MapReadObservation.open(request, registry, metrics, nanoTime);
        } catch (RuntimeException ignored) {
            chain.doFilter(request, response);
            return;
        }
        boolean failed = false;
        try (observation) {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException exception) {
                failed = true;
                throw exception;
            } finally {
                observation.finish(response.getStatus(), failed);
                diagnostics.write(observation, response, failed);
            }
        }
    }
}
