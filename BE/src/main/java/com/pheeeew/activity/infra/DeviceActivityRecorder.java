package com.pheeeew.activity.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.pheeeew.activity.application.DeviceDailyActivityService;
import com.pheeeew.activity.infra.metrics.DeviceActivityRecordMetrics;
import com.pheeeew.common.logging.ExceptionLogFormatter;
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
    private final DeviceActivityRecordMetrics metrics;
    private final DeviceActivityCache activityCache = new DeviceActivityCache(10_000, Caffeine.newBuilder());
    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();
    private final AtomicReference<Instant> nextLogAt = new AtomicReference<>(Instant.MIN);

    public DeviceActivityRecorder(
            DeviceDailyActivityService activityService,
            @Qualifier("deviceActivityExecutor") Executor executor,
            DeviceActivityRecordMetrics metrics,
            Clock clock
    ) {
        this.activityService = activityService;
        this.executor = executor;
        this.clock = clock;
        this.metrics = metrics;
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
                    metrics.recordSaveFailure();
                    logFailure("save_failed", failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            activityCache.release(marker);
            metrics.recordQueueRejection();
            logFailure("queue_rejected", failure);
        }
    }

    private void logFailure(String reason, RuntimeException failure) {
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
