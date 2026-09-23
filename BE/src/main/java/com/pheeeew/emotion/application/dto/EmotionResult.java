package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.Emotion;
import java.time.Instant;

public record EmotionResult(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        String memo,
        String nickname
) {

    public static EmotionResult from(Emotion emotion) {
        return new EmotionResult(
                emotion.getId(),
                emotion.getLongitude(),
                emotion.getLatitude(),
                emotion.getCreatedAt(),
                emotion.getMemo(),
                emotion.getNickname()
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
