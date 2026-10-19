package com.pheeeew.emotion.application.dto;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
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

    public static EmotionCursor initialWithinBounds(EmotionSearchBounds bounds, Instant snapshotAt, EmotionSearchFilter filter) {
        return ofWithinBounds(bounds, snapshotAt, snapshotAt, Long.MAX_VALUE, filter);
    }

    public static EmotionCursor ofWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastItemCreatedAt,
            long lastId,
            EmotionSearchFilter filter
    ) {
        Objects.requireNonNull(bounds);
        return new EmotionCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, filter);
    }

    public static EmotionCursor initialWithoutBounds(Instant snapshotAt, EmotionSearchFilter filter) {
        return ofWithoutBounds(snapshotAt, snapshotAt, Long.MAX_VALUE, filter);
    }

    public static EmotionCursor ofWithoutBounds(Instant snapshotAt, Instant lastItemCreatedAt, long lastId, EmotionSearchFilter filter) {
        return new EmotionCursor(null, snapshotAt, lastItemCreatedAt, lastId, filter);
    }

    public UUID groupId() {
        if (groupIds().size() > 1) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
        return groupIds().isEmpty() ? null : groupIds().getFirst();
    }

    public List<UUID> groupIds() {
        return filter.groupIds();
    }

    public void validateSnapshotAt(Instant now) {
        if (snapshotAt.isAfter(now)) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public void validateRequestedGroups(List<UUID> requestedGroupIds) {
        if (requestedGroupIds != null && (requestedGroupIds.isEmpty()
                || !new HashSet<>(requestedGroupIds).equals(new HashSet<>(groupIds())))) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
    }

    public void validateRequestedFilter(
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            List<UUID> groupIds
    ) {
        if ((startDate == null) != (endDate == null)
                || startDate != null && !startDate.equals(filter.startDate())
                || endDate != null && !endDate.equals(filter.endDate())
                || contentType != null && contentType != filter.contentType()
                || scope != null && scope != filter.scope()) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
        validateRequestedGroups(groupIds);
    }

    public EmotionCursor next(Instant lastItemCreatedAt, long lastId) {
        return new EmotionCursor(bounds, snapshotAt, lastItemCreatedAt, lastId, filter);
    }
}
