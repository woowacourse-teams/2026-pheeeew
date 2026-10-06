package com.pheeeew.emotion.application;

import com.pheeeew.emotion.domain.PressCounts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class EmotionPressMetrics {

    private static final String CLASSIFY_NAME = "pheeeew.emotion.press.classify";
    private static final String CLASSIFY_DESCRIPTION =
            "Region classification duration per region press request, including failed calls";
    private static final String CLAMPED_NAME = "pheeeew.emotion.press.clamped";
    private static final String CLAMPED_DESCRIPTION = "Press count dropped by a press limit, per request that hit it";

    private final MeterRegistry registry;
    private final Timer classifyAssigned;
    private final Timer classifyUnassigned;
    private final Timer classifyFailed;
    private final Counter regionUnassignedRejections;
    private final DistributionSummary applied;
    private final DistributionSummary perStateClamped;
    private final DistributionSummary totalClamped;
    private final DistributionSummary states;
    private final Counter emptyRequests;
    private final Counter deadlocks;

    public EmotionPressMetrics(MeterRegistry registry) {
        this.registry = registry;
        classifyAssigned = Timer.builder(CLASSIFY_NAME)
                .description(CLASSIFY_DESCRIPTION)
                .tag("result", "assigned")
                .register(registry);
        classifyUnassigned = Timer.builder(CLASSIFY_NAME)
                .description(CLASSIFY_DESCRIPTION)
                .tag("result", "unassigned")
                .register(registry);
        classifyFailed = Timer.builder(CLASSIFY_NAME)
                .description(CLASSIFY_DESCRIPTION)
                .tag("result", "failed")
                .register(registry);
        regionUnassignedRejections = Counter.builder("pheeeew.emotion.press.rejected")
                .description("Region press requests rejected before applying any press")
                .tag("reason", "region_unassigned")
                .register(registry);
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
    }

    public Timer.Sample startClassify() {
        return Timer.start(registry);
    }

    public void recordClassify(Timer.Sample sample, boolean assigned) {
        sample.stop(assigned ? classifyAssigned : classifyUnassigned);
    }

    public void recordClassifyFailure(Timer.Sample sample) {
        sample.stop(classifyFailed);
    }

    public void recordRegionUnassigned() {
        regionUnassignedRejections.increment();
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
