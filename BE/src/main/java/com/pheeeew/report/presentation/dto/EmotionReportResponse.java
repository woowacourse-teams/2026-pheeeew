package com.pheeeew.report.presentation.dto;

import com.pheeeew.report.application.dto.EmotionReportResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SighReportResponse")
public record EmotionReportResponse(
        @Schema(description = "신고 ID", example = "7")
        Long id,

        @Schema(description = "신고된 한숨 ID", example = "42")
        Long sighId,

        @Schema(description = "신고 사유", example = "광고성 게시물입니다")
        String reason,

        @Schema(description = "신고 시각", example = "2026-09-01T02:44:00Z")
        Instant createdAt
) {

    public static EmotionReportResponse from(EmotionReportResult result) {
        return new EmotionReportResponse(
                result.id(),
                result.emotionId(),
                result.reason(),
                result.createdAt()
        );
    }
}
