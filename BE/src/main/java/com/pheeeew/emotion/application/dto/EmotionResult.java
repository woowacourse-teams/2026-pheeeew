package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

public record EmotionResult(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        EmotionState state,
        String memo,
        String nickname
) {

    public static EmotionResult from(Emotion emotion) {
        return new EmotionResult(
                emotion.getId(),
                emotion.getLongitude(),
                emotion.getLatitude(),
                emotion.getCreatedAt(),
                emotion.getState(),
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
        return new EmotionResult(id, longitude, latitude, createdAt, null, memo, nickname);
    }
}
