package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.Emotion;

public record EmotionCreateResponse(Long id) {

    public static EmotionCreateResponse from(Emotion emotion) {
        return new EmotionCreateResponse(emotion.getId());
    }
}
