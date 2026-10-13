package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import java.time.Instant;
import java.util.List;

public interface EmotionRegionQueryRepository {

    List<RegionEmotionSummaryProjection> findSummariesByRegionCodes(
            List<String> regionCodes,
            EmotionSearchFilter filter,
            Long deviceId,
            Instant snapshotAt
    );
}
