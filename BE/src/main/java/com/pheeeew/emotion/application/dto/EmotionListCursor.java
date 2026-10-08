package com.pheeeew.emotion.application.dto;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmotionListCursor(EmotionSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId, UUID groupId) {

    public EmotionListCursor {
        Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(lastItemCreatedAt);
        if (lastItemCreatedAt.isAfter(snapshotAt)) {
            throw new IllegalArgumentException("마지막 감정 생성 시각은 스냅샷 시각보다 늦을 수 없습니다.");
        }
        if (lastId < 1) {
            throw new IllegalArgumentException("마지막 감정 ID는 1 이상이어야 합니다.");
        }
    }

    public static EmotionListCursor initialWithinBounds(EmotionSearchBounds bounds, Instant snapshotAt) {
        return initialWithinBounds(bounds, snapshotAt, null);
    }

    public static EmotionListCursor initialWithinBounds(EmotionSearchBounds bounds, Instant snapshotAt, UUID groupId) {
        Objects.requireNonNull(bounds);
        return new EmotionListCursor(bounds, snapshotAt, snapshotAt, Long.MAX_VALUE, groupId);
    }

    public static EmotionListCursor ofWithinBounds(EmotionSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId) {
        Objects.requireNonNull(bounds);
        return new EmotionListCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, null);
    }

    public static EmotionListCursor initialWithoutBounds(Instant snapshotAt, UUID groupId) {
        return ofWithoutBounds(snapshotAt, snapshotAt, Long.MAX_VALUE, groupId);
    }

    public static EmotionListCursor ofWithoutBounds(Instant snapshotAt, Instant lastItemCreatedAt, long lastId, UUID groupId) {
        return new EmotionListCursor(null, snapshotAt, lastItemCreatedAt, lastId, groupId);
    }

    public void validateSnapshotAt(Instant now) {
        if (snapshotAt.isAfter(now)) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public void validateRequestedGroup(UUID requestedGroupId) {
        if (requestedGroupId != null && !requestedGroupId.equals(groupId)) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public EmotionListCursor next(Instant lastItemCreatedAt, long lastId) {
        return new EmotionListCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, groupId);
    }
}
