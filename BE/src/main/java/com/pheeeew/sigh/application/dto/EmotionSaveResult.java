package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;

public record EmotionSaveResult(EmotionResult emotion, boolean created, SighLikeResult like) {

    public static EmotionSaveResult of(EmotionResult emotion, boolean created, SighLikeResult like) {
        return new EmotionSaveResult(emotion, created, like);
    }
}
