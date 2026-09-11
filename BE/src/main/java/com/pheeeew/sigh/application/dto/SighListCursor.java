package com.pheeeew.sigh.application.dto;

import java.time.Instant;
import java.util.Objects;

public record SighListCursor(SighSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId) {

    public SighListCursor {
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(lastItemCreatedAt);
        if (lastItemCreatedAt.isAfter(snapshotAt)) {
            throw new IllegalArgumentException("마지막 한숨 생성 시각은 스냅샷 시각보다 늦을 수 없습니다.");
        }
        if (lastId < 1) {
            throw new IllegalArgumentException("마지막 한숨 ID는 1 이상이어야 합니다.");
        }
    }

    public static SighListCursor initial(SighSearchBounds bounds, Instant snapshotAt) {
        return new SighListCursor(bounds, snapshotAt, snapshotAt, Long.MAX_VALUE);
    }

    public static SighListCursor of(SighSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId) {
        return new SighListCursor(bounds, snapshotAt, lastItemCreatedAt, lastId);
    }

    public SighListCursor next(Instant lastItemCreatedAt, long lastId) {
        return SighListCursor.of(bounds, snapshotAt, lastItemCreatedAt, lastId);
    }
}
