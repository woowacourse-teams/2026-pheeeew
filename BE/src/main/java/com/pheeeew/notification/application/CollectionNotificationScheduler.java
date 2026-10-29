package com.pheeeew.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CollectionNotificationScheduler {

    private final CollectionNotificationService collectionNotificationService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void saveClaimableOutbox() {
        collectionNotificationService.saveClaimableOutbox();
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    public void saveReminderOutbox() {
        collectionNotificationService.saveReminderOutbox();
    }
}
