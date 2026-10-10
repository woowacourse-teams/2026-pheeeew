package com.pheeeew.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final String EVENT_REQUEST_LOG_SKIPPED = "http_request_log_skipped";
    private static final String UNMAPPED_ROUTE = "UNMAPPED";
    private static final String OTHER_METHOD = "OTHER";

    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            long durationMillis,
            Exception unhandledException
    ) {
        try {
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
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", EVENT_REQUEST_LOG_SKIPPED)
                    .addKeyValue("skippedBy", exception.getClass().getName())
                    .log("요청 로그를 남기지 못했습니다");
        }
    }

    private void logServerError(HttpServletRequest request, int status, long durationMillis, Throwable failure) {
        Map<String, String> params = RequestLogValues.from(request);
        LoggingEventBuilder event = createRequestEvent(log.atError(), request, status, durationMillis)
                .addKeyValue("event", EVENT_REQUEST_FAILED)
                .addKeyValue("errorCode", request.getAttribute(ERROR_CODE_ATTRIBUTE));
        if (!params.isEmpty()) {
            event.addKeyValue("params", params);
        }
        if (failure != null) {
            exceptionLogFormatter.addFailureFields(event, failure);
        }
        event.log("HTTP 요청 처리에 실패했습니다");
    }

    private LoggingEventBuilder createRequestEvent(
            LoggingEventBuilder event,
            HttpServletRequest request,
            int status,
            long durationMillis
    ) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        addTimings(event, request);
        return event.addKeyValue("method", resolveMethod(request.getMethod()))
                .addKeyValue("route", route == null ? UNMAPPED_ROUTE : route.toString())
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMillis);
    }

    private void addTimings(LoggingEventBuilder event, HttpServletRequest request) {
        Map<RequestTiming.Stage, RequestTiming.Measurement> snapshot = RequestTiming.snapshot(request);
        if (snapshot.isEmpty()) {
            return;
        }
        Map<String, Map<String, Number>> timings = new LinkedHashMap<>();
        for (RequestTiming.Stage stage : RequestTiming.Stage.values()) {
            RequestTiming.Measurement measured = snapshot.get(stage);
            if (measured != null) {
                timings.put(stage.name().toLowerCase(Locale.ROOT), Map.of(
                        "count", measured.count(), "totalMs", measured.totalNanos() / 1_000_000.0,
                        "maxMs", measured.maxNanos() / 1_000_000.0));
            }
        }
        // 서비스 경계는 내부 DB 단계를 포함할 수 있으므로 단계들을 합산하지 않는다.
        event.addKeyValue("timings", timings);
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
