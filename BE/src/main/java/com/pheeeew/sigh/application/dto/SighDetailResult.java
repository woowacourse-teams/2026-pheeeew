package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.SighListProjection;

public record SighDetailResult(SighResult sigh, SighLikeResult like) {

    public static SighDetailResult from(EmotionDetailProjection projection) {
        return new SighDetailResult(
                SighResult.from(projection.getSigh()),
                SighLikeResult.of(projection.getLiked(), projection.getSigh().getLikeCount())
        );
    }

    public static SighDetailResult from(SighListProjection projection) {
        return new SighDetailResult(
                SighResult.of(
                        projection.getId(), projection.getLongitude(), projection.getLatitude(),
                        projection.getCreatedAt(), projection.getMemo(), projection.getNickname()
                ),
                SighLikeResult.of(projection.getLiked(), projection.getLikeCount())
        );
    }
}
