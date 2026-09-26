package com.pheeeew.report.application.dto;

import com.pheeeew.report.domain.EmotionReport;
import java.time.Instant;

public record EmotionReportResult(Long id, Long emotionId, String reason, Instant createdAt, boolean created) {

    public static EmotionReportResult of(EmotionReport report, boolean created) {
        return new EmotionReportResult(
                report.getId(),
                report.getEmotionId(),
                report.getReason(),
                report.getCreatedAt(),
                created
        );
    }
}
