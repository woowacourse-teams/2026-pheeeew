package com.pheeeew.appversion.infra.metrics;

import com.pheeeew.appversion.domain.AppPlatform;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AppVersionMetricsFilter extends OncePerRequestFilter {

    private static final String VERSION_PATH = "/api/v2/app/version";

    private final Map<AppPlatform, Counter> successfulChecks = new EnumMap<>(AppPlatform.class);

    public AppVersionMetricsFilter(MeterRegistry registry) {
        for (AppPlatform platform : AppPlatform.values()) {
            successfulChecks.put(platform, Counter.builder("pheeeew.app.version.checks")
                    .description("Successful app version policy HTTP request count")
                    .tag("platform", platform.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                || !(request.getContextPath() + VERSION_PATH).equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return;
        }

        AppPlatform platform = AppPlatform.from(request.getParameter("platform"));
        successfulChecks.get(platform).increment();
    }
}
