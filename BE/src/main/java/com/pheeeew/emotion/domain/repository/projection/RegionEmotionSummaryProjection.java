package com.pheeeew.emotion.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;

public interface RegionEmotionSummaryProjection {

    String getRegionCode();

    long getTotalCount();

    EmotionState getRepresentativeState();
}
