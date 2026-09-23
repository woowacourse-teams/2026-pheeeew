package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.EmotionListProjection;

public record EmotionDetailResult(EmotionResult emotion, SighLikeResult like) {

    public static EmotionDetailResult from(EmotionDetailProjection projection) {
        return new EmotionDetailResult(
                EmotionResult.from(projection.getEmotion()),
                SighLikeResult.of(projection.getLiked(), projection.getEmotion().getLikeCount())
        );
    }

    public static EmotionDetailResult from(EmotionListProjection projection) {
        return new EmotionDetailResult(
                EmotionResult.of(
                        projection.getId(), projection.getLongitude(), projection.getLatitude(),
                        projection.getCreatedAt(), projection.getMemo(), projection.getNickname()
                ),
                SighLikeResult.of(projection.getLiked(), projection.getLikeCount())
        );
    }
}
