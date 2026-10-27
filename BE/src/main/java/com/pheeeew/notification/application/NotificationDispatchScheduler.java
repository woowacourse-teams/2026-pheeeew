package com.pheeeew.notification.application;

import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import com.pheeeew.notification.domain.outbox.NotificationOutboxType;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatchScheduler {

    private static final Duration DISPATCH_DELAY = Duration.ofSeconds(10);

    private final NotificationDispatcher notificationDispatcher;
    private final boolean automaticStart;
    private final List<ScheduledExecutorService> executors = new ArrayList<>();

    public NotificationDispatchScheduler(
            NotificationDispatcher notificationDispatcher,
            @Value("${pheeeew.notification.dispatch.automatic-start:true}") boolean automaticStart
    ) {
        this.notificationDispatcher = notificationDispatcher;
        this.automaticStart = automaticStart;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (automaticStart) {
            start();
        }
    }

    public void start() {
        for (NotificationOutboxType type : NotificationOutboxType.values()) {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("notification-dispatch-" + type.name()).daemon().factory());
            executor.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(
                            () -> notificationDispatcher.dispatch(type), new ScheduledTaskErrorHandler()),
                    0, DISPATCH_DELAY.toMillis(), TimeUnit.MILLISECONDS);
            executors.add(executor);
        }
    }

    @PreDestroy
    public void stop() {
        executors.forEach(ScheduledExecutorService::shutdownNow);
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteOldOutbox() {
        notificationDispatcher.deleteOldOutbox();
    }
}
