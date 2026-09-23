package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SighV1Properties")
public record EmotionV1Properties(
        @Schema(description = "한숨 생성 시각", example = "2026-08-31T10:30:00Z")
        Instant createdAt
) {

    public static EmotionV1Properties from(Instant createdAt) {
        return new EmotionV1Properties(createdAt);
    }
}
