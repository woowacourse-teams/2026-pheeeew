package com.pheeeew.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID = "correlationId";

    private final RequestLogWriter logWriter = new RequestLogWriter();
    private final LongSupplier nanoTime;

    public RequestLoggingFilter() {
        this(System::nanoTime);
    }

    RequestLoggingFilter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousCorrelationId = MDC.get(CORRELATION_ID);
        setCorrelationId(response);
        long startedAt = nanoTime.getAsLong();
        Exception unhandledException = null;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            unhandledException = exception;
            throw exception;
        } finally {
            try {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - startedAt);
                logWriter.write(request, response, durationMillis, unhandledException);
            } finally {
                restoreCorrelationId(previousCorrelationId);
            }
        }
    }

    private void setCorrelationId(HttpServletResponse response) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
    }

    private void restoreCorrelationId(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CORRELATION_ID);
        } else {
            MDC.put(CORRELATION_ID, previousCorrelationId);
        }
    }
}
