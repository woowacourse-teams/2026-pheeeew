package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.common.logging.RequestLoggingFilter;
import com.pheeeew.common.logging.RequestTiming;
import com.pheeeew.common.logging.RequestTiming.Measurement;
import com.pheeeew.common.logging.RequestTiming.Stage;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.EmotionPressMetrics.QueryOperation;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class EmotionPressQueryMetricsAspectTest {

    private final MockClock clock = new MockClock();
    private final PrometheusMeterRegistry registry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), clock);

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @ParameterizedTest
    @CsvSource({"increase,increase,PRESS_INCREASE,true", "increase,increase,PRESS_INCREASE,false",
            "findByPressDateAndDeviceId,my_daily,PRESS_MY_DAILY,true",
            "findByPressDateAndDeviceId,my_daily,PRESS_MY_DAILY,false",
            "sumByPressDate,daily_total,PRESS_DAILY_TOTAL,true", "sumByPressDate,daily_total,PRESS_DAILY_TOTAL,false"})
    void JDBC_작업별_성공과_실패_시간을_구분한다(
            String method, String operation, Stage stage, boolean succeeded
    ) throws Throwable {
        // given
        var aspect = new EmotionPressQueryMetricsAspect(new EmotionPressMetrics(registry), clock::monotonicTime);
        ProceedingJoinPoint joinPoint = queryJoinPoint(method);
        Object result = new Object();
        IllegalStateException original = new IllegalStateException("SQL failure");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            clock.addSeconds(231);
            if (!succeeded) {
                throw original;
            }
            return result;
        });
        // when / then
        var timings = inRequest(() -> {
            if (succeeded) {
                assertThat(aspect.recordQuery(joinPoint)).isSameAs(result);
            } else {
                assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
            }
        });
        long durationNanos = TimeUnit.SECONDS.toNanos(231);
        assertThat(timings).containsExactlyEntriesOf(Map.of(stage, Measurement.of(1, durationNanos, durationNanos)));
        String outcome = succeeded ? "success" : "error";
        Timer timer = registry.get("pheeeew.emotion.press.query").tags("operation", operation, "outcome", outcome).timer();
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(231);
        assertThat(timer.getId().getTags()).containsExactlyInAnyOrder(Tag.of("operation", operation), Tag.of("outcome", outcome));
        assertThat(registry.find("pheeeew.emotion.press.query").timers()).hasSize(6).allSatisfy(other ->
                assertThat(other.count()).isEqualTo(other == timer ? 1 : 0));
        assertThat(registry.scrape().lines().filter(line -> line.startsWith("pheeeew_emotion_press_query_seconds_bucket{")
                && line.contains("le=\"300.0\"") && line.contains("operation=\"" + operation + "\"")
                && line.contains("outcome=\"" + outcome + "\""))).singleElement().satisfies(line ->
                assertThat(Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1))).isEqualTo(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 계측_기록_실패가_결과와_원래_예외를_바꾸지_않는다(boolean succeeded) throws Throwable {
        // given
        EmotionPressMetrics metrics = mock(EmotionPressMetrics.class);
        Timer.Sample sample = Timer.start(registry);
        when(metrics.startQuery()).thenReturn(sample);
        doThrow(new IllegalStateException("metrics failure")).when(metrics).recordQuery(sample, QueryOperation.INCREASE, succeeded);
        ProceedingJoinPoint joinPoint = queryJoinPoint("increase");
        Object result = new Object();
        IllegalArgumentException original = new IllegalArgumentException("SQL failure");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            if (!succeeded) {
                throw original;
            }
            return result;
        });
        // when / then
        var aspect = new EmotionPressQueryMetricsAspect(metrics, clock::monotonicTime);
        var timings = inRequest(() -> {
            if (succeeded) {
                assertThat(aspect.recordQuery(joinPoint)).isSameAs(result);
            } else {
                assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
            }
        });
        assertThat(timings).containsExactlyEntriesOf(Map.of(Stage.PRESS_INCREASE, Measurement.of(1, 0, 0)));
        verify(metrics).recordQuery(sample, QueryOperation.INCREASE, succeeded);
    }

    @Test
    void 반복_저장의_성공과_실패를_한_요청의_호출수_누적시간_최대시간으로_묶는다() throws Throwable {
        // given
        var aspect = new EmotionPressQueryMetricsAspect(new EmotionPressMetrics(registry), clock::monotonicTime);
        var joinPoint = queryJoinPoint("increase");
        var original = new IllegalStateException("SQL failure");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            clock.addSeconds(1);
            return null;
        }).thenAnswer(invocation -> {
            clock.addSeconds(2);
            throw original;
        });

        // when
        var timings = inRequest(() -> {
            aspect.recordQuery(joinPoint);
            assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
        });

        // then
        assertThat(timings).containsExactlyEntriesOf(Map.of(Stage.PRESS_INCREASE,
                Measurement.of(2, TimeUnit.SECONDS.toNanos(3), TimeUnit.SECONDS.toNanos(2))));
        assertThat(registry.get("pheeeew.emotion.press.query").tags("operation", "increase", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("pheeeew.emotion.press.query").tags("operation", "increase", "outcome", "error")
                .timer().count()).isEqualTo(1);
    }

    private Map<Stage, Measurement> inRequest(ThrowingCallable operation) throws Exception {
        var request = new MockHttpServletRequest();
        var snapshot = new AtomicReference<Map<Stage, Measurement>>();
        new RequestLoggingFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            try {
                operation.call();
                snapshot.set(RequestTiming.snapshot(request));
            } catch (Throwable error) {
                throw new ServletException(error);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        assertThat(RequestTiming.snapshot(request)).isEmpty();
        return snapshot.get();
    }

    private ProceedingJoinPoint queryJoinPoint(String method) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(method);
        return joinPoint;
    }
}
