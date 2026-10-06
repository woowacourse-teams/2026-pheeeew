package com.pheeeew.groups.application;

import com.pheeeew.emotion.domain.PressCounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GroupPressMetrics {

    private static final String CLAMPED_NAME = "pheeeew.group.press.clamped";
    private static final String CLAMPED_DESCRIPTION = "Press count dropped by a press limit, per request that hit it";
    private static final String REQUESTS_NAME = "pheeeew.group.press.requests";
    private static final String REQUESTS_DESCRIPTION = "Press requests by request body format";

    private final DistributionSummary applied;
    private final DistributionSummary perStateClamped;
    private final DistributionSummary totalClamped;
    private final DistributionSummary states;
    private final Counter bundledRequests;
    private final Counter legacyRequests;
    private final Counter emptyRequests;
    private final Counter deadlocks;

    public GroupPressMetrics(MeterRegistry registry) {
        applied = DistributionSummary.builder("pheeeew.group.press.applied")
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
        states = DistributionSummary.builder("pheeeew.group.press.states")
                .description("Applied emotion state count per request that applied at least one press")
                .register(registry);
        bundledRequests = Counter.builder(REQUESTS_NAME)
                .description(REQUESTS_DESCRIPTION)
                .tag("format", "counts")
                .register(registry);
        legacyRequests = Counter.builder(REQUESTS_NAME)
                .description(REQUESTS_DESCRIPTION)
                .tag("format", "state")
                .register(registry);
        emptyRequests = Counter.builder("pheeeew.group.press.empty")
                .description("Press requests that applied nothing after skipping zero counts")
                .register(registry);
        deadlocks = Counter.builder("pheeeew.group.press.deadlocks")
                .description("Press upsert attempts aborted by a PostgreSQL deadlock")
                .register(registry);
    }

    public void recordFormat(boolean bundled) {
        if (bundled) {
            bundledRequests.increment();
            return;
        }

        legacyRequests.increment();
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
}
