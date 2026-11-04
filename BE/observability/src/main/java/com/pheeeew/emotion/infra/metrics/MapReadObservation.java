package com.pheeeew.emotion.infra.metrics;

import com.pheeeew.common.logging.RequestTiming;
import com.pheeeew.common.logging.RequestTimingStage;
import com.pheeeew.emotion.infra.metrics.MapReadMetrics.Stage;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 동기 지도 요청의 관측 문맥. 보안 필터에서 MVC 요청 문맥을 강제로 바인딩하지 않는다. */
public final class MapReadObservation implements AutoCloseable {

    private static final ThreadLocal<MapReadObservation> CURRENT = new ThreadLocal<>();
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final MapReadObservation previous;
    private final HttpServletRequest request;
    private final MeterRegistry registry;
    private final MapReadMetrics metrics;
    private final LongSupplier nanoTime;
    private final Route route;
    private final long started;
    private Long mvcStarted;
    private Long controllerStarted;
    private Long controllerEnded;
    private Long mvcEnded;
    private String query = "unclassified";
    private String page = "unknown";
    private String scope = "unknown";
    private String contentType = "unknown";
    private Decision decision = Decision.UNCLASSIFIED;
    private boolean eligible;
    private boolean cacheAttempted;
    private boolean finished;
    private String outcome = "success";
    private long durationNanos;

    private MapReadObservation(HttpServletRequest request, MeterRegistry registry, MapReadMetrics metrics, LongSupplier nanoTime) {
        this.previous = CURRENT.get();
        this.request = request;
        this.registry = registry;
        this.metrics = metrics;
        this.nanoTime = nanoTime;
        this.route = route(request);
        if (route == null) {
            throw new IllegalArgumentException("지도 GET 요청만 관측합니다.");
        }
        this.started = nanoTime.getAsLong();
        CURRENT.set(this);
    }

    public static MapReadObservation open(HttpServletRequest request, MeterRegistry registry) {
        return open(request, registry, System::nanoTime);
    }

    static MapReadObservation open(HttpServletRequest request, MeterRegistry registry, LongSupplier nanoTime) {
        return open(request, registry, new MapReadMetrics(registry), nanoTime);
    }

    static MapReadObservation open(HttpServletRequest request, MeterRegistry registry, MapReadMetrics metrics, LongSupplier nanoTime) {
        return new MapReadObservation(request, registry, metrics, nanoTime);
    }

    public static Route route(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/api/v2/emotions/map" -> new Route("v2", "pins");
            case "/api/v2/emotions/map/regions" -> new Route("v2", "regions");
            case "/api/v4/emotions/map" -> new Route("v4", "pins");
            case "/api/v4/emotions/map/regions" -> new Route("v4", "regions");
            default -> null;
        };
    }

    public static String apiVersion() {
        MapReadObservation current = CURRENT.get();
        if (current != null) {
            return current.route.apiVersion();
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Route observed = route(attributes.getRequest());
            if (observed != null) {
                return observed.apiVersion();
            }
        }
        return "unscoped";
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static void query(LocalDate startDate, LocalDate endDate, boolean firstPage, boolean defaultFilter, Clock clock) {
        observe(current -> {
            current.page = firstPage ? "first" : "next";
            if (!defaultFilter) {
                current.query = "other";
            } else if (startDate == null && endDate == null) {
                current.query = "all_default";
            } else if (endDate != null && endDate.minusMonths(1).equals(startDate)) {
                current.query = endDate.equals(LocalDate.ofInstant(clock.instant(), KOREA))
                        ? "month_current_default" : "month_other_default";
            } else {
                current.query = "other";
            }
        });
    }

    public static void filters(String scope, String contentType) {
        observe(current -> {
            current.scope = Set.of("ALL", "MY", "GROUP").contains(scope) ? scope : "unknown";
            current.contentType = Set.of("ALL", "MEMO", "AUDIO").contains(contentType) ? contentType : "unknown";
        });
    }

    public static void decision(Decision decision) {
        observe(current -> {
            current.decision = decision;
            current.eligible |= decision == Decision.ELIGIBLE;
            current.cacheAttempted |= decision == Decision.CACHE_ATTEMPT;
        });
    }

    public static void cacheResult(String cache, String result) {
        observe(current -> {
            if (!current.cacheAttempted || !current.route.route().equals(cache)) {
                return;
            }
            switch (result) {
                case "hit" -> current.decision = Decision.CACHE_HIT;
                case "miss" -> current.decision = Decision.CACHE_LOAD;
                case "fallback" -> current.decision = Decision.CACHE_FALLBACK;
                default -> { }
            }
        });
    }

    public static void mvcStarted() {
        observe(current -> current.mvcStarted = current.nanoTime.getAsLong());
    }

    public static void controllerStarted() {
        observe(current -> current.controllerStarted = current.nanoTime.getAsLong());
    }

    public static void controllerEnded() {
        observe(current -> current.controllerEnded = current.nanoTime.getAsLong());
    }

    public static void mvcEnded() {
        observe(current -> current.mvcEnded = current.nanoTime.getAsLong());
    }

    public static void recordStage(Stage stage, long durationNanos, boolean succeeded) {
        observe(current -> current.record(stage, durationNanos, succeeded));
    }

    public static <S extends Enum<S> & RequestTimingStage> void recordTiming(S stage, long durationNanos) {
        MapReadObservation current = CURRENT.get();
        if (current == null) {
            RequestTiming.record(stage, durationNanos);
        } else {
            RequestTiming.record(current.request, stage, durationNanos);
        }
    }

    public void finish(int status, boolean failed) {
        if (finished) {
            return;
        }
        finished = true;
        outcome = failed || status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
        try {
            long ended = nanoTime.getAsLong();
            durationNanos = ended - started;
            boolean succeeded = !failed && status < 400;
            record(Stage.MAP_FILTER_TOTAL, durationNanos, succeeded);
            interval(Stage.MAP_PRE_MVC, started, mvcStarted, succeeded);
            interval(Stage.MAP_MVC_PRE_CONTROLLER, mvcStarted, controllerStarted, succeeded);
            interval(Stage.MAP_RESPONSE_PROCESSING, controllerEnded, mvcEnded, succeeded);
            interval(Stage.MAP_FILTER_RETURN, mvcEnded, ended, succeeded);
        } catch (RuntimeException ignored) {
            // 타이머 장애가 경로 카운터나 원래 응답을 막지 않는다.
        }
        try {
            registry.counter("pheeeew.map.read.requests", "api_version", route.apiVersion(), "route", route.route(),
                    "query", query, "page", page, "decision", decision.name().toLowerCase(Locale.ROOT),
                    "outcome", outcome).increment();
        } catch (RuntimeException ignored) {
            // 계측 실패 시에도 요청 문맥은 close에서 정리한다.
        }
    }

    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }

    public Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("apiVersion", route.apiVersion());
        details.put("route", route.route());
        details.put("query", query);
        details.put("page", page);
        details.put("decision", decision.name().toLowerCase(Locale.ROOT));
        details.put("outcome", outcome);
        details.put("scope", scope);
        details.put("contentType", contentType);
        details.put("eligible", eligible);
        details.put("cacheAttempted", cacheAttempted);
        details.put("durationMs", durationMillis());
        Map<String, Object> timings = new LinkedHashMap<>();
        RequestTiming.snapshot(request).forEach((stage, measured) -> timings.put(stage.name().toLowerCase(Locale.ROOT),
                Map.of("count", measured.count(), "totalMs", measured.totalNanos() / 1_000_000.0,
                        "maxMs", measured.maxNanos() / 1_000_000.0)));
        details.put("timings", timings);
        return details;
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    private void interval(Stage stage, Long from, Long to, boolean succeeded) {
        if (from != null && to != null) {
            record(stage, to - from, succeeded);
        }
    }

    private void record(Stage stage, long durationNanos, boolean succeeded) {
        try {
            metrics.record(stage, durationNanos, succeeded);
        } catch (RuntimeException ignored) {
            // 지표와 요청별 시간 기록의 실패를 분리한다.
        }
        try {
            RequestTiming.record(request, stage, durationNanos);
        } catch (RuntimeException ignored) {
            // 관측은 원래 조회 결과에 영향을 주지 않는다.
        }
    }

    private static void observe(Consumer<MapReadObservation> action) {
        MapReadObservation current = CURRENT.get();
        if (current != null) {
            try {
                action.accept(current);
            } catch (RuntimeException ignored) {
                // 조회·인증·응답과 관측 장애를 격리한다.
            }
        }
    }

    public record Route(String apiVersion, String route) {
    }

    public enum Decision {
        UNCLASSIFIED, ELIGIBLE, CACHE_ATTEMPT, CACHE_HIT, CACHE_LOAD, CACHE_FALLBACK,
        BYPASS_PERIOD, BYPASS_FILTER, BYPASS_TRANSACTION, BYPASS_LEVEL, EMPTY
    }
}
