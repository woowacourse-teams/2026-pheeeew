package com.pheeeew.region.infra.metrics;

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
import com.pheeeew.region.infra.metrics.RegionQueryMetrics.Operation;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RegionQueryMetricsAspectTest {

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @ParameterizedTest
    @CsvSource({
            "findIntersectingRegions,intersecting_regions,REGIONS_INTERSECTION,true",
            "findIntersectingRegions,intersecting_regions,REGIONS_INTERSECTION,false",
            "areBoundariesVerified,boundaries_verified,REGIONS_BOUNDARIES,true",
            "areBoundariesVerified,boundaries_verified,REGIONS_BOUNDARIES,false",
            "isAggregationReady,aggregation_ready,REGIONS_AGGREGATION_READY,true",
            "isAggregationReady,aggregation_ready,REGIONS_AGGREGATION_READY,false",
            "findEmdCode,emd_code,REGION_EMD_CODE,true", "findEmdCode,emd_code,REGION_EMD_CODE,false"
    })
    void 작업별_성공과_실패를_구분하고_분_단위_지연을_기록한다(
            String methodName, String operation, Stage stage, boolean succeeded
    ) throws Throwable {
        // given
        AtomicLong nanoTime = new AtomicLong();
        var aspect = new RegionQueryMetricsAspect(new RegionQueryMetrics(registry), nanoTime::get);
        ProceedingJoinPoint joinPoint = queryJoinPoint(methodName);
        Object result = new Object();
        IllegalStateException original = new IllegalStateException("query failure");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            nanoTime.set(TimeUnit.SECONDS.toNanos(231));
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
        Timer recorded = registry.get("pheeeew.region.query").tags("operation", operation, "outcome", outcome).timer();
        assertThat(recorded.count()).isEqualTo(1);
        assertThat(recorded.totalTime(TimeUnit.SECONDS)).isEqualTo(231);
        assertThat(recorded.getId().getTags()).containsExactlyInAnyOrder(
                Tag.of("operation", operation), Tag.of("outcome", outcome));
        assertThat(registry.getMeters()).hasSize(8).allSatisfy(meter ->
                assertThat(((Timer) meter).count()).isEqualTo(meter == recorded ? 1 : 0));
        assertThat(bucketCount(operation, outcome, "120.0")).isZero();
        assertThat(bucketCount(operation, outcome, "300.0")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 계측_기록이_실패해도_조회_결과와_원래_예외를_유지한다(boolean succeeded) throws Throwable {
        // given
        RegionQueryMetrics metrics = mock(RegionQueryMetrics.class);
        doThrow(new IllegalStateException("metrics failure")).when(metrics)
                .recordQuery(Operation.INTERSECTING_REGIONS, 0, succeeded);
        var aspect = new RegionQueryMetricsAspect(metrics, () -> 0);
        ProceedingJoinPoint joinPoint = queryJoinPoint("findIntersectingRegions");
        Object result = new Object();
        IllegalArgumentException original = new IllegalArgumentException("query failure");
        if (succeeded) {
            when(joinPoint.proceed()).thenReturn(result);
        } else {
            when(joinPoint.proceed()).thenThrow(original);
        }

        // when / then
        var timings = inRequest(() -> {
            if (succeeded) {
                assertThat(aspect.recordQuery(joinPoint)).isSameAs(result);
            } else {
                assertThatThrownBy(() -> aspect.recordQuery(joinPoint)).isSameAs(original);
            }
        });
        assertThat(timings).containsExactlyEntriesOf(Map.of(Stage.REGIONS_INTERSECTION, Measurement.of(1, 0, 0)));
        verify(metrics).recordQuery(Operation.INTERSECTING_REGIONS, 0, succeeded);
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

    private ProceedingJoinPoint queryJoinPoint(String methodName) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        return joinPoint;
    }

    private double bucketCount(String operation, String outcome, String boundary) {
        String bucket = registry.scrape().lines()
                .filter(line -> line.startsWith("pheeeew_region_query_seconds_bucket{"))
                .filter(line -> line.contains("operation=\"" + operation + "\""))
                .filter(line -> line.contains("outcome=\"" + outcome + "\""))
                .filter(line -> line.contains("le=\"" + boundary + "\""))
                .findFirst().orElseThrow();
        return Double.parseDouble(bucket.substring(bucket.lastIndexOf(' ') + 1));
    }
}
