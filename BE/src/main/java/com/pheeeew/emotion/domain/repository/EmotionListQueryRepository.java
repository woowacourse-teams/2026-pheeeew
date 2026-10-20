package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import java.time.Instant;
import java.util.List;

public interface EmotionListQueryRepository {

    List<Emotion> findVisiblePageWithoutBounds(
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    );

    List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            boolean contentOnly,
            int limit
    );

    List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    );
}
