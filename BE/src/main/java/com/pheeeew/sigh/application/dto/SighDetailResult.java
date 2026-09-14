package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.repository.projection.SighDetailProjection;

public record SighDetailResult(SighResult sigh, SighLikeResult like) {

    public static SighDetailResult from(SighDetailProjection projection) {
        return new SighDetailResult(
                SighResult.from(projection.getSigh()),
                SighLikeResult.of(projection.getLiked(), projection.getSigh().getLikeCount())
        );
    }
}
