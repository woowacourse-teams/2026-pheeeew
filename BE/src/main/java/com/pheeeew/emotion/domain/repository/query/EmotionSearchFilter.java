package com.pheeeew.emotion.domain.repository.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EmotionSearchFilter(
        LocalDate startDate,
        LocalDate endDate,
        EmotionContentFilter contentType,
        EmotionSearchScope scope,
        List<UUID> groupIds
) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public EmotionSearchFilter {
        if (!isDateRangeValid(startDate, endDate)) {
            throw new IllegalArgumentException("0001~9999년의 시작일과 종료일을 함께 전달하고 시작일은 종료일 이하여야 합니다.");
        }
        if (contentType == null) {
            contentType = EmotionContentFilter.ALL;
        }
        groupIds = groupIds == null ? List.of() : groupIds;
        if (groupIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("그룹 ID 목록에는 null을 전달할 수 없습니다.");
        }
        groupIds = groupIds.stream().distinct().sorted().toList();
        if (scope == null) {
            scope = groupIds.isEmpty() ? EmotionSearchScope.ALL : EmotionSearchScope.GROUP;
        }
        if ((scope == EmotionSearchScope.GROUP) != !groupIds.isEmpty()) {
            throw new IllegalArgumentException("GROUP 범위에서만 그룹 ID를 필수로 전달해야 합니다.");
        }
    }

    public static EmotionSearchFilter from(List<UUID> groupIds) {
        return of(null, null, null, null, groupIds);
    }

    public static EmotionSearchFilter of(
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            List<UUID> groupIds
    ) {
        if (groupIds != null && groupIds.isEmpty()) {
            throw new IllegalArgumentException("그룹 ID 목록을 지정하면 하나 이상의 ID가 필요합니다.");
        }

        return new EmotionSearchFilter(startDate, endDate, contentType, scope, groupIds);
    }

    public static boolean isDateRangeValid(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return startDate == null && endDate == null;
        }
        return startDate.getYear() >= 1 && endDate.getYear() <= 9999 && !startDate.isAfter(endDate);
    }

    public Instant startInclusive() {
        return startDate == null ? null : startDate.atStartOfDay(KOREA).toInstant();
    }

    public Instant endExclusive() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay(KOREA).toInstant();
    }
}
