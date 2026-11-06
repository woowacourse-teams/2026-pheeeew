package com.pheeeew.collection.application.dto;

import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.util.List;

public record CollectionEmotionResult(Long id, Instant createdAt, EmotionState state, String memo, PlaybackUrl audio,
        List<EmotionEmojiResult> emojis) {

    public static CollectionEmotionResult of(Emotion emotion, PlaybackUrl audio, List<EmotionEmojiResult> emojis) {
        return new CollectionEmotionResult(emotion.getId(), emotion.getCreatedAt(), emotion.getState(),
                emotion.getMemo(), audio, List.copyOf(emojis));
    }
}
