package com.pheeeew.collection.domain.repository.projection;

import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionCardDetail(UUID publicId, LocalDate date, EmotionState state,
        long stampCount, long pressCount, String version, CollectionAssetPaths paths) {

    public static CollectionCardDetail of(UUID publicId, LocalDate date, EmotionState state,
            long stampCount, long pressCount, String version, CollectionAssetPaths paths) {
        return new CollectionCardDetail(publicId, date, state, stampCount, pressCount, version, paths);
    }
}
