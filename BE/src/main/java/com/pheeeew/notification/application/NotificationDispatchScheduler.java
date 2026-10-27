package com.pheeeew.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class NotificationDispatchScheduler {

    private final NotificationDispatcher notificationDispatcher;

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT30S")
    public void dispatch() {
        notificationDispatcher.dispatch();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteOldOutbox() {
        notificationDispatcher.deleteOldOutbox();
    }
}
