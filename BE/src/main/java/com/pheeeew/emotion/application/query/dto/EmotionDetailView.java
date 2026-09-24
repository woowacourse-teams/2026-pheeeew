package com.pheeeew.emotion.application.query.dto;

import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.util.List;

public record EmotionDetailView(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        EmotionState state,
        double rotationDegrees,
        String memo,
        String nickname,
        List<EmotionEmojiResult> emojis
) {

    public static EmotionDetailView of(Emotion emotion, List<EmotionEmojiResult> emojis) {
        return new EmotionDetailView(
                emotion.getId(), emotion.getLongitude(), emotion.getLatitude(), emotion.getCreatedAt(),
                emotion.getState(), emotion.getRotationDegrees(), emotion.getMemo(), emotion.getNickname(),
                List.copyOf(emojis)
        );
    }
}
