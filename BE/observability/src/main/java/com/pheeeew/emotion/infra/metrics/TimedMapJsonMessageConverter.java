package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 기존 JSON 변환기의 직렬화와 출력 쓰기를 함께 측정한다. 클라이언트 수신 완료 시간은 포함하지 않는다. */
public final class TimedMapJsonMessageConverter implements SmartHttpMessageConverter<Object> {

    private final SmartHttpMessageConverter<Object> delegate;
    private final MeterRegistry meters;
    private final ConcurrentMap<TimerKey, Timer> timers = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public TimedMapJsonMessageConverter(SmartHttpMessageConverter<Object> delegate, MeterRegistry meters) {
        this(delegate, meters, System::nanoTime);
    }

    TimedMapJsonMessageConverter(SmartHttpMessageConverter<Object> delegate, MeterRegistry meters, LongSupplier nanoTime) {
        this.delegate = delegate;
        this.nanoTime = nanoTime;
        this.meters = meters;
    }

    @Override
    public boolean canRead(ResolvableType type, MediaType mediaType) {
        return delegate.canRead(type, mediaType);
    }

    @Override
    public boolean canRead(Class<?> type, MediaType mediaType) {
        return delegate.canRead(type, mediaType);
    }

    @Override
    public Object read(ResolvableType type, HttpInputMessage input, Map<String, Object> hints) throws IOException {
        return delegate.read(type, input, hints);
    }

    @Override
    public Object read(Class<?> type, HttpInputMessage input) throws IOException {
        return delegate.read(type, input);
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return delegate.canWrite(type, valueClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> type, MediaType mediaType) {
        return delegate.canWrite(type, mediaType);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return delegate.getSupportedMediaTypes();
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> type) {
        return delegate.getSupportedMediaTypes(type);
    }

    @Override
    public void write(Object value, ResolvableType type, MediaType mediaType, HttpOutputMessage output,
                      Map<String, Object> hints) throws IOException {
        measure(() -> delegate.write(value, type, mediaType, output, hints));
    }

    @Override
    public void write(Object value, MediaType mediaType, HttpOutputMessage output) throws IOException {
        measure(() -> delegate.write(value, mediaType, output));
    }

    private void measure(WriteAction action) throws IOException {
        MapReadObservation.Route route = currentRoute();
        if (route == null) {
            action.write();
            return;
        }

        long started = nanoTime.getAsLong();
        boolean succeeded = false;
        try {
            action.write();
            succeeded = true;
        } finally {
            long duration = nanoTime.getAsLong() - started;
            record(route, succeeded, duration);
            MapReadObservation.recordStage(MapReadMetrics.Stage.MAP_JSON_WRITE, duration, succeeded);
        }
    }

    private MapReadObservation.Route currentRoute() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }

        return MapReadObservation.route(attributes.getRequest());
    }

    private Timer newTimer(TimerKey key) {
        return Timer.builder("pheeeew.map.response.write")
                    .description("JSON converter write including serialization and output I/O; excludes client receive time")
                    .tags("route", key.route().route(), "outcome", key.outcome(), "api_version", key.route().apiVersion())
                    .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(10),
                            Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
                            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1))
                    .register(meters);
    }

    private void record(MapReadObservation.Route route, boolean succeeded, long elapsedNanos) {
        try {
            TimerKey key = new TimerKey(route, succeeded ? "success" : "error");
            timers.computeIfAbsent(key, this::newTimer).record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 계측 실패가 응답 본문이나 원래 쓰기 예외를 바꾸지 않게 한다.
        }
    }

    private record TimerKey(MapReadObservation.Route route, String outcome) {
    }

    @FunctionalInterface
    private interface WriteAction {
        void write() throws IOException;
    }
}
