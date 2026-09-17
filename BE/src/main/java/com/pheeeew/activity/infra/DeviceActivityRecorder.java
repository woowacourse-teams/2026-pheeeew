package com.pheeeew.activity.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.pheeeew.activity.application.DeviceDailyActivityService;
import com.pheeeew.common.logging.ExceptionLogFormatter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeviceActivityRecorder {

    private final DeviceDailyActivityService activityService;
    private final Executor executor;
    private final Clock clock;
    private final Counter saveFailures;
    private final Counter rejectedTasks;
    private final DeviceActivityCache activityCache = new DeviceActivityCache(10_000, Caffeine.newBuilder());
    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();
    private final AtomicReference<Instant> nextLogAt = new AtomicReference<>(Instant.MIN);

    public DeviceActivityRecorder(
            DeviceDailyActivityService activityService,
            @Qualifier("deviceActivityExecutor") Executor executor,
            MeterRegistry registry,
            Clock clock
    ) {
        this.activityService = activityService;
        this.executor = executor;
        this.clock = clock;
        this.saveFailures = registry.counter("pheeeew.activity.record.failures", "reason", "save_failed");
        this.rejectedTasks = registry.counter("pheeeew.activity.record.failures", "reason", "queue_rejected");
    }

    public void record(UUID devicePublicId, Instant occurredAt) {
        DeviceActivityCache.ActivityMarker marker = activityCache.acquire(devicePublicId, occurredAt, clock.instant());
        if (marker == null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    activityService.save(devicePublicId, occurredAt);
                } catch (RuntimeException failure) {
                    activityCache.release(marker);
                    recordFailure(saveFailures, "save_failed", failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            activityCache.release(marker);
            recordFailure(rejectedTasks, "queue_rejected", failure);
        }
    }

    private void recordFailure(Counter counter, String reason, RuntimeException failure) {
        counter.increment();
        Instant now = clock.instant();
        Instant next = nextLogAt.get();
        if (now.isBefore(next) || !nextLogAt.compareAndSet(next, now.plusSeconds(60))) {
            return;
        }
        // SQL 예외 원문과 작업 인자에는 기기 식별자가 포함될 수 있다.
        log.atError()
                .addKeyValue("event", "device_activity_record_failed")
                .addKeyValue("reason", reason)
                .addKeyValue("errorStack", exceptionLogFormatter.format(failure))
                .log("기기 활동 기록에 실패했습니다");
    }
}
