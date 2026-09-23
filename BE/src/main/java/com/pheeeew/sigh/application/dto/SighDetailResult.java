package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.EmotionListProjection;

public record SighDetailResult(EmotionResult sigh, SighLikeResult like) {

    public static SighDetailResult from(EmotionDetailProjection projection) {
        return new SighDetailResult(
                EmotionResult.from(projection.getEmotion()),
                SighLikeResult.of(projection.getLiked(), projection.getEmotion().getLikeCount())
        );
    }

    public static SighDetailResult from(EmotionListProjection projection) {
        return new SighDetailResult(
                EmotionResult.of(
                        projection.getId(), projection.getLongitude(), projection.getLatitude(),
                        projection.getCreatedAt(), projection.getMemo(), projection.getNickname()
                ),
                SighLikeResult.of(projection.getLiked(), projection.getLikeCount())
        );
    }
}
