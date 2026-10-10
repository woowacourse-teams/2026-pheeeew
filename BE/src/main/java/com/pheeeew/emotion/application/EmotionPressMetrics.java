package com.pheeeew.emotion.application;

import com.pheeeew.emotion.domain.PressCounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmotionPressMetrics {

    private static final String CLAMPED_NAME = "pheeeew.emotion.press.clamped";
    private static final String CLAMPED_DESCRIPTION = "Press count dropped by a press limit, per request that hit it";

    private final DistributionSummary applied;
    private final DistributionSummary perStateClamped;
    private final DistributionSummary totalClamped;
    private final DistributionSummary states;
    private final Counter emptyRequests;
    private final Counter deadlocks;
    private final MeterRegistry registry;
    private final Map<QueryOperation, Timer> querySuccesses = new EnumMap<>(QueryOperation.class);
    private final Map<QueryOperation, Timer> queryErrors = new EnumMap<>(QueryOperation.class);

    public EmotionPressMetrics(MeterRegistry registry) {
        this.registry = registry;
        applied = DistributionSummary.builder("pheeeew.emotion.press.applied")
                .description("Applied press count per request, after clamping")
                .register(registry);
        perStateClamped = DistributionSummary.builder(CLAMPED_NAME)
                .description(CLAMPED_DESCRIPTION)
                .tag("limit", "per_state")
                .register(registry);
        totalClamped = DistributionSummary.builder(CLAMPED_NAME)
                .description(CLAMPED_DESCRIPTION)
                .tag("limit", "total")
                .register(registry);
        states = DistributionSummary.builder("pheeeew.emotion.press.states")
                .description("Applied emotion state count per request that applied at least one press")
                .register(registry);
        emptyRequests = Counter.builder("pheeeew.emotion.press.empty")
                .description("Press requests that applied nothing after skipping zero counts")
                .register(registry);
        deadlocks = Counter.builder("pheeeew.emotion.press.deadlocks")
                .description("Press upsert attempts aborted by a PostgreSQL deadlock")
                .register(registry);
        for (QueryOperation operation : QueryOperation.values()) {
            querySuccesses.put(operation, registerQuery(operation, "success"));
            queryErrors.put(operation, registerQuery(operation, "error"));
        }
    }

    public void recordApplied(PressCounts pressCounts) {
        int appliedTotal = pressCounts.appliedTotal();
        if (appliedTotal == 0) {
            emptyRequests.increment();
            return;
        }

        applied.record(appliedTotal);
        states.record(pressCounts.presses().size());
        if (pressCounts.perStateDropped() > 0) {
            perStateClamped.record(pressCounts.perStateDropped());
        }
        if (pressCounts.totalDropped() > 0) {
            totalClamped.record(pressCounts.totalDropped());
        }
    }

    public void recordDeadlock() {
        deadlocks.increment();
    }

    public Timer.Sample startQuery() {
        return Timer.start(registry);
    }

    public void recordQuery(Timer.Sample sample, QueryOperation operation, boolean succeeded) {
        sample.stop((succeeded ? querySuccesses : queryErrors).get(operation));
    }

    private Timer registerQuery(QueryOperation operation, String outcome) {
        return Timer.builder("pheeeew.emotion.press.query")
                .description("Personal press repository operation duration including JDBC communication and result mapping")
                .tags("operation", operation.name().toLowerCase(Locale.ROOT), "outcome", outcome)
                // 분 단위 지연을 구분하는 진단용 경계이며, 합의된 SLO는 아니다.
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(500),
                        Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10),
                        Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120), Duration.ofSeconds(300))
                .register(registry);
    }

    public enum QueryOperation {
        INCREASE,
        MY_DAILY,
        DAILY_TOTAL
    }
}
