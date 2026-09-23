package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.EmotionLikeResult;

public record EmotionSaveResult(EmotionResult emotion, boolean created, EmotionLikeResult like) {

    public static EmotionSaveResult of(EmotionResult emotion, boolean created, EmotionLikeResult like) {
        return new EmotionSaveResult(emotion, created, like);
    }
}
