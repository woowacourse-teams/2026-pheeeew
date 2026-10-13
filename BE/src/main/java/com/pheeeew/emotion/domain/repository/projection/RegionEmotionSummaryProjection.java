package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public record RegionEmotionSummaryProjection(String regionCode, long totalCount, EmotionState representativeState) {

    public static RegionEmotionSummaryProjection of(String regionCode, long totalCount, EmotionState representativeState) {
        return new RegionEmotionSummaryProjection(regionCode, totalCount, representativeState);
    }
}
