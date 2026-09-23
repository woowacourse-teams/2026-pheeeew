package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import com.pheeeew.emotion.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionListProjection;

public record EmotionDetailResult(EmotionResult emotion, EmotionLikeResult like) {

    public static EmotionDetailResult from(EmotionDetailProjection projection) {
        return new EmotionDetailResult(
                EmotionResult.from(projection.getEmotion()),
                EmotionLikeResult.of(projection.getLiked(), projection.getEmotion().getLikeCount())
        );
    }

    public static EmotionDetailResult from(EmotionListProjection projection) {
        return new EmotionDetailResult(
                EmotionResult.of(
                        projection.getId(), projection.getLongitude(), projection.getLatitude(),
                        projection.getCreatedAt(), projection.getMemo(), projection.getNickname()
                ),
                EmotionLikeResult.of(projection.getLiked(), projection.getLikeCount())
        );
    }
}
