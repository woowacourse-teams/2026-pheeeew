package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 일시적인 개발 진단용 표본이다. 전체 요청 비율·분위수의 계산에 사용하지 않는다. */
public final class MapReadDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(MapReadDiagnostics.class);
    private final boolean enabled;
    private final double sampleRate;
    private final long slowMillis;
    private final int maximumPerSecond;
    private final MeterRegistry registry;
    private final DoubleSupplier random;
    private final LongSupplier nanoTime;
    private long windowStarted;
    private int emitted;

    public MapReadDiagnostics(boolean enabled, double sampleRate, long slowMillis, int maximumPerSecond, MeterRegistry registry) {
        this(enabled, sampleRate, slowMillis, maximumPerSecond, registry,
                () -> ThreadLocalRandom.current().nextDouble(), System::nanoTime);
    }

    MapReadDiagnostics(boolean enabled, double sampleRate, long slowMillis, int maximumPerSecond,
                       MeterRegistry registry, DoubleSupplier random, LongSupplier nanoTime) {
        if (!Double.isFinite(sampleRate) || sampleRate < 0 || sampleRate > 1 || slowMillis < 0 || maximumPerSecond < 1) {
            throw new IllegalArgumentException("지도 진단 표본율·지연 기준·로그 상한이 올바르지 않습니다.");
        }
        this.enabled = enabled;
        this.sampleRate = sampleRate;
        this.slowMillis = slowMillis;
        this.maximumPerSecond = maximumPerSecond;
        this.registry = registry;
        this.random = random;
        this.nanoTime = nanoTime;
    }

    public void write(MapReadObservation observation, HttpServletResponse response, boolean failed) {
        if (!enabled) {
            return;
        }
        try {
            boolean slow = observation.durationMillis() >= slowMillis;
            if (!slow && random.getAsDouble() >= sampleRate) {
                return;
            }
            if (!acquire()) {
                registry.counter("pheeeew.map.diagnostic.logs", "result", "suppressed").increment();
                return;
            }
            var event = log.atInfo().addKeyValue("event", "map_read_sample")
                    .addKeyValue("sampleReason", slow ? "slow" : "random")
                    .addKeyValue("observedStatus", response.getStatus())
                    .addKeyValue("unhandledFailure", failed)
                    .addKeyValue("responseCommitted", response.isCommitted());
            observation.details().forEach(event::addKeyValue);
            String length = response.getHeader("Content-Length");
            if (length != null && length.matches("[0-9]{1,18}")) {
                event.addKeyValue("responseContentLength", Long.parseLong(length));
            }
            String encoding = response.getHeader("Content-Encoding");
            String normalized = encoding == null ? "identity" : encoding.toLowerCase(Locale.ROOT);
            event.addKeyValue("contentEncoding", Set.of("identity", "gzip").contains(normalized) ? normalized : "other")
                    .log("지도 요청 진단 표본");
            registry.counter("pheeeew.map.diagnostic.logs", "result", "sampled").increment();
        } catch (RuntimeException ignored) {
            // 로그·지표 장애가 원래 인증·조회·응답 예외를 덮어쓰지 않는다.
        }
    }

    private synchronized boolean acquire() {
        long now = nanoTime.getAsLong();
        if (emitted == 0 || now - windowStarted >= TimeUnit.SECONDS.toNanos(1)) {
            windowStarted = now;
            emitted = 0;
        }
        if (emitted >= maximumPerSecond) {
            return false;
        }
        emitted++;
        return true;
    }
}
