package com.pheeeew.collection.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionTodayProgress(EmotionState state, Long stampCount, Long pressCount, Card card) {

    public static CollectionTodayProgress of(EmotionState state, Long stampCount, Long pressCount, Card card) {
        return new CollectionTodayProgress(state, stampCount, pressCount, card);
    }

    public record Card(UUID publicId, LocalDate date, long cycle, int combinationIndex,
            String version, Instant claimedAt, CollectionAssetPaths paths) {

        public static Card of(UUID publicId, LocalDate date, long cycle, int combinationIndex,
                String version, Instant claimedAt, CollectionAssetPaths paths) {
            return new Card(publicId, date, cycle, combinationIndex, version, claimedAt, paths);
        }
    }
}
