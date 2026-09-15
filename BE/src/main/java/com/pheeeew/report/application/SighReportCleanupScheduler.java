package com.pheeeew.report.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SighReportCleanupScheduler {

    private final SighReportService sighReportService;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void deleteReportedOverThreshold() {
        sighReportService.deleteReportedOverThreshold();
    }
}
