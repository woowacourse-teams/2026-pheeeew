package com.pheeeew.report.application.dto;

import com.pheeeew.report.domain.SighReport;
import java.time.Instant;

public record SighReportResult(Long id, Long sighId, String reason, Instant createdAt, boolean created) {

    public static SighReportResult of(SighReport report, boolean created) {
        return new SighReportResult(
                report.getId(),
                report.getSighId(),
                report.getReason(),
                report.getCreatedAt(),
                created
        );
    }
}
