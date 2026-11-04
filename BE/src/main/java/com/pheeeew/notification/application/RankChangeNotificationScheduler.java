package com.pheeeew.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RankChangeNotificationScheduler {

    private final RankChangeNotificationService rankChangeNotificationService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void compareRanks() {
        rankChangeNotificationService.compareRanks();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void deleteOldRounds() {
        rankChangeNotificationService.deleteOldRounds();
    }
}
