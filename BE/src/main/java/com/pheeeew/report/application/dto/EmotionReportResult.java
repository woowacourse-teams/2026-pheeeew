package com.pheeeew.report.application.dto;

import com.pheeeew.report.domain.SighReport;
import java.time.Instant;

public record EmotionReportResult(Long id, Long sighId, String reason, Instant createdAt, boolean created) {

    public static EmotionReportResult of(SighReport report, boolean created) {
        return new EmotionReportResult(
                report.getId(),
                report.getSighId(),
                report.getReason(),
                report.getCreatedAt(),
                created
        );
    }
}
