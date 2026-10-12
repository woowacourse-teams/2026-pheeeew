package com.pheeeew.emotion.application.dto;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record EmotionCursor(EmotionSearchBounds bounds, Instant snapshotAt, Instant lastItemCreatedAt, long lastId, EmotionSearchFilter filter) {

    public EmotionCursor {
        Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(lastItemCreatedAt);
        Objects.requireNonNull(filter);
        if (lastItemCreatedAt.isAfter(snapshotAt)) {
            throw new IllegalArgumentException("마지막 감정 생성 시각은 스냅샷 시각보다 늦을 수 없습니다.");
        }
        if (lastId < 1) {
            throw new IllegalArgumentException("마지막 감정 ID는 1 이상이어야 합니다.");
        }
    }

    public static EmotionCursor initialWithinBounds(EmotionSearchBounds bounds, Instant snapshotAt, UUID groupId) {
        return ofWithinBounds(bounds, snapshotAt, snapshotAt, Long.MAX_VALUE, groupId);
    }

    public static EmotionCursor ofWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastItemCreatedAt,
            long lastId,
            UUID groupId
    ) {
        Objects.requireNonNull(bounds);
        return new EmotionCursor(bounds, snapshotAt, lastItemCreatedAt, lastId,
                EmotionSearchFilter.of(null, null, null, null, groupId));
    }

    public static EmotionCursor initialWithoutBounds(Instant snapshotAt, UUID groupId) {
        return ofWithoutBounds(snapshotAt, snapshotAt, Long.MAX_VALUE, groupId);
    }

    public static EmotionCursor ofWithoutBounds(Instant snapshotAt, Instant lastItemCreatedAt, long lastId, UUID groupId) {
        return new EmotionCursor(null, snapshotAt, lastItemCreatedAt, lastId,
                EmotionSearchFilter.of(null, null, null, null, groupId));
    }

    public UUID groupId() {
        return filter.groupId();
    }

    public EmotionCursor withFilter(EmotionSearchFilter filter) {
        return new EmotionCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, filter);
    }

    public void validateSnapshotAt(Instant now) {
        if (snapshotAt.isAfter(now)) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public void validateRequestedGroup(UUID requestedGroupId) {
        if (requestedGroupId != null && !requestedGroupId.equals(groupId())) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public void validateRequestedFilter(
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            UUID groupId
    ) {
        if ((startDate == null) != (endDate == null)
                || startDate != null && !startDate.equals(filter.startDate())
                || endDate != null && !endDate.equals(filter.endDate())
                || contentType != null && contentType != filter.contentType()
                || scope != null && scope != filter.scope()) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
        validateRequestedGroup(groupId);
    }

    public EmotionCursor next(Instant lastItemCreatedAt, long lastId) {
        return new EmotionCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, filter);
    }
}
