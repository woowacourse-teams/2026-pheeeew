package com.pheeeew.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
public class RequestLogWriter {

    public static final String FAILURE_ATTRIBUTE = RequestLogWriter.class.getName() + ".failure";
    public static final String ERROR_CODE_ATTRIBUTE = RequestLogWriter.class.getName() + ".errorCode";
    private static final long SLOW_REQUEST_MILLIS = 1000;
    private static final String EVENT_REQUEST_FAILED = "http_request_failed";
    private static final String EVENT_REQUEST_SLOW = "http_request_slow";
    private static final String UNMAPPED_ROUTE = "UNMAPPED";
    private static final String OTHER_METHOD = "OTHER";

    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            long durationMillis,
            Exception unhandledException
    ) {
        int status = unhandledException == null ? response.getStatus() : 500;

        if (status >= 500) {
            Throwable failure = unhandledException != null ? unhandledException
                    : (Throwable) request.getAttribute(FAILURE_ATTRIBUTE);
            logServerError(request, status, durationMillis, failure);
        } else if (durationMillis >= SLOW_REQUEST_MILLIS) {
            createRequestEvent(log.atWarn(), request, status, durationMillis)
                    .addKeyValue("event", EVENT_REQUEST_SLOW)
                    .log("HTTP request exceeded {}ms", SLOW_REQUEST_MILLIS);
        }
    }

    private void logServerError(HttpServletRequest request, int status, long durationMillis, Throwable failure) {
        LoggingEventBuilder event = createRequestEvent(log.atError(), request, status, durationMillis)
                .addKeyValue("event", EVENT_REQUEST_FAILED)
                .addKeyValue("errorCode", request.getAttribute(ERROR_CODE_ATTRIBUTE));
        if (failure != null) {
            event.addKeyValue("errorStack", exceptionLogFormatter.format(failure));
        }
        event.log("HTTP request failed");
    }

    private LoggingEventBuilder createRequestEvent(
            LoggingEventBuilder event,
            HttpServletRequest request,
            int status,
            long durationMillis
    ) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return event.addKeyValue("method", resolveMethod(request.getMethod()))
                .addKeyValue("route", route == null ? UNMAPPED_ROUTE : route.toString())
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMillis);
    }

    private String resolveMethod(String requestMethod) {
        for (HttpMethod method : HttpMethod.values()) {
            if (method.matches(requestMethod)) {
                return method.name();
            }
        }
        return OTHER_METHOD;
    }
}
