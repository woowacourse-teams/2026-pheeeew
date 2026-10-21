package com.pheeeew.emotion.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    private final Timer[] pins;
    private final Timer[] regions;
    private final LongSupplier nanoTime;

    public TimedMapJsonMessageConverter(SmartHttpMessageConverter<Object> delegate, MeterRegistry meters) {
        this(delegate, meters, System::nanoTime);
    }

    TimedMapJsonMessageConverter(SmartHttpMessageConverter<Object> delegate, MeterRegistry meters, LongSupplier nanoTime) {
        this.delegate = delegate;
        this.nanoTime = nanoTime;
        this.pins = new Timer[] {newTimer(meters, "pins", "success"), newTimer(meters, "pins", "error")};
        this.regions = new Timer[] {newTimer(meters, "regions", "success"), newTimer(meters, "regions", "error")};
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
        String route = currentRoute();
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
            record(route, succeeded, nanoTime.getAsLong() - started);
        }
    }

    private String currentRoute() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        if (!"GET".equals(request.getMethod())) {
            return null;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/api/v2/emotions/map" -> "pins";
            case "/api/v2/emotions/map/regions" -> "regions";
            default -> null;
        };
    }

    private Timer newTimer(MeterRegistry meters, String route, String outcome) {
        try {
            return Timer.builder("pheeeew.map.response.write")
                    .description("JSON converter write including serialization and output I/O; excludes client receive time")
                    .tags("route", route, "outcome", outcome)
                    .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(10),
                            Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
                            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1))
                    .register(meters);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void record(String route, boolean succeeded, long elapsedNanos) {
        Timer timer = ("pins".equals(route) ? pins : regions)[succeeded ? 0 : 1];
        if (timer == null) {
            return;
        }

        try {
            timer.record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 계측 실패가 응답 본문이나 원래 쓰기 예외를 바꾸지 않게 한다.
        }
    }

    @FunctionalInterface
    private interface WriteAction {
        void write() throws IOException;
    }
}
