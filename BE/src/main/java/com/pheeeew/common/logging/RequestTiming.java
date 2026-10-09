package com.pheeeew.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// 동기 Servlet 요청 한 개의 단계별 누적 값이다. 같은 요청의 비동기·병렬 기록은 지원하지 않는다.
public final class RequestTiming {

    static final String ATTRIBUTE = RequestTiming.class.getName();
    private final Map<Stage, Measurement> stages = new EnumMap<>(Stage.class);

    private RequestTiming() {
    }

    static Object begin(HttpServletRequest request) {
        Object previous = request.getAttribute(ATTRIBUTE);
        request.setAttribute(ATTRIBUTE, new RequestTiming());
        return previous;
    }

    static void restore(HttpServletRequest request, Object previous) {
        if (previous == null) {
            request.removeAttribute(ATTRIBUTE);
        } else {
            request.setAttribute(ATTRIBUTE, previous);
        }
    }

    public static void record(Stage stage, long durationNanos) {
        if (durationNanos >= 0 && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(ATTRIBUTE) instanceof RequestTiming timing) {
            timing.add(stage, durationNanos);
        }
    }

    public static Map<Stage, Measurement> snapshot(HttpServletRequest request) {
        // 필터에서 읽는 시점에는 내부 MVC의 RequestContextHolder가 이미 해제될 수 있다.
        return request.getAttribute(ATTRIBUTE) instanceof RequestTiming timing ? Map.copyOf(timing.stages) : Map.of();
    }

    private void add(Stage stage, long durationNanos) {
        Measurement previous = stages.get(stage);
        stages.put(stage, previous == null
                ? Measurement.of(1, durationNanos, durationNanos)
                : Measurement.of(previous.count() + 1, previous.totalNanos() + durationNanos,
                        Math.max(previous.maxNanos(), durationNanos)));
    }

    public enum Stage {
        REGIONS_BOUNDARIES,
        REGIONS_INTERSECTION,
        REGIONS_AGGREGATION_READY,
        REGIONS_SUMMARY,
        REGION_EMD_CODE,
        PRESS_INCREASE,
        PRESS_MY_DAILY,
        PRESS_DAILY_TOTAL,
        REGIONS_SERVICE,
        PRESS_SERVICE,
        PRESS_MY_SERVICE,
        PRESS_TOTAL_SERVICE
    }

    public record Measurement(long count, long totalNanos, long maxNanos) {

        public static Measurement of(long count, long totalNanos, long maxNanos) {
            return new Measurement(count, totalNanos, maxNanos);
        }
    }
}
