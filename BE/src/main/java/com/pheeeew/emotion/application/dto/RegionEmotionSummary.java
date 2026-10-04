package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;

public record RegionEmotionSummary(long totalCount, EmotionState representativeState) {

    public static RegionEmotionSummary from(RegionEmotionSummaryProjection projection) {
        return of(projection.getTotalCount(), projection.getRepresentativeState());
    }

    public static RegionEmotionSummary of(long totalCount, EmotionState representativeState) {
        return new RegionEmotionSummary(totalCount, representativeState);
    }
}
