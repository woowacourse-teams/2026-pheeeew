package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.EmotionPressMetrics.QueryOperation;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmotionPressQueryMetricsAspectTest {

    private final MockClock clock = new MockClock();
    private final PrometheusMeterRegistry registry =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), clock);

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @ParameterizedTest
    @CsvSource({"increase,increase,true", "increase,increase,false",
            "findByPressDateAndDeviceId,my_daily,true", "findByPressDateAndDeviceId,my_daily,false",
            "sumByPressDate,daily_total,true", "sumByPressDate,daily_total,false"})
    void JDBC_작업별_성공과_실패_시간을_구분한다(String method, String operation, boolean succeeded) throws Throwable {
        // given
        var aspect = new EmotionPressQueryMetricsAspect(new EmotionPressMetrics(registry));
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
        if (succeeded) {
            assertThat(aspect.recordQuery(joinPoint)).isSameAs(result);
        } else {
            assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
        }
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
        var aspect = new EmotionPressQueryMetricsAspect(metrics);
        if (succeeded) {
            assertThat(aspect.recordQuery(joinPoint)).isSameAs(result);
        } else {
            assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
        }
        verify(metrics).recordQuery(sample, QueryOperation.INCREASE, succeeded);
    }

    private ProceedingJoinPoint queryJoinPoint(String method) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(method);
        return joinPoint;
    }
}
