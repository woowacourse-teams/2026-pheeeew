package com.pheeeew.appversion.infra.metrics;

import com.pheeeew.appversion.domain.AppPlatform;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AppVersionMetricsFilter extends OncePerRequestFilter {

    private static final String VERSION_PATH = "/api/v2/app/version";

    private final AppVersionMetrics metrics;

    public AppVersionMetricsFilter(AppVersionMetrics metrics) {
        this.metrics = metrics;
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
        switch (platform) {
            case ANDROID -> metrics.recordAndroidCheck();
            case IOS -> metrics.recordIosCheck();
        }
    }
}
