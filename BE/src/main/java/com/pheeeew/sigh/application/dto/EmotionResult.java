package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.domain.Emotion;
import java.time.Instant;

public record EmotionResult(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        String memo,
        String nickname
) {

    public static EmotionResult from(Emotion sigh) {
        return new EmotionResult(
                sigh.getId(),
                sigh.getLongitude(),
                sigh.getLatitude(),
                sigh.getCreatedAt(),
                sigh.getMemo(),
                sigh.getNickname()
        );
    }

    public static EmotionResult of(
            Long id,
            double longitude,
            double latitude,
            Instant createdAt,
            String memo,
            String nickname
    ) {
        return new EmotionResult(id, longitude, latitude, createdAt, memo, nickname);
    }
}
