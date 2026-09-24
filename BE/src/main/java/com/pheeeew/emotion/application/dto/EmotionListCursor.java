package com.pheeeew.emotion.application.dto;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import java.time.Instant;
import java.util.Objects;

public record EmotionListCursor(EmotionSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId) {

    public EmotionListCursor {
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(lastItemCreatedAt);
        if (lastItemCreatedAt.isAfter(snapshotAt)) {
            throw new IllegalArgumentException("마지막 감정 생성 시각은 스냅샷 시각보다 늦을 수 없습니다.");
        }
        if (lastId < 1) {
            throw new IllegalArgumentException("마지막 감정 ID는 1 이상이어야 합니다.");
        }
    }

    public static EmotionListCursor initial(EmotionSearchBounds bounds, Instant snapshotAt) {
        return new EmotionListCursor(bounds, snapshotAt, snapshotAt, Long.MAX_VALUE);
    }

    public static EmotionListCursor of(EmotionSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId) {
        return new EmotionListCursor(bounds, snapshotAt, lastItemCreatedAt, lastId);
    }

    public EmotionListCursor next(Instant lastItemCreatedAt, long lastId) {
        return EmotionListCursor.of(bounds, snapshotAt, lastItemCreatedAt, lastId);
    }
}
