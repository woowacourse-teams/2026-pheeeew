package com.pheeeew.device.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class DeviceChallengeCleanupScheduler {

    private final DeviceChallengeService deviceChallengeService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void deleteExpired() {
        deviceChallengeService.deleteExpired();
    }
}
