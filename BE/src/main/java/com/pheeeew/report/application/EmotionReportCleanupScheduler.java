package com.pheeeew.report.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EmotionReportCleanupScheduler {

    private final SighReportService emotionReportService;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void deleteReportedOverThreshold() {
        emotionReportService.deleteReportedOverThreshold();
    }
}
