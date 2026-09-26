package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;

public record EmotionListItemView(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        EmotionState state,
        double rotationDegrees,
        String memo,
        String nickname
) {

    public static EmotionListItemView from(Emotion emotion) {
        return new EmotionListItemView(
                emotion.getId(), emotion.getLongitude(), emotion.getLatitude(), emotion.getCreatedAt(),
                emotion.getState(), emotion.getRotationDegrees(), emotion.getMemo(), emotion.getNickname()
        );
    }
}
