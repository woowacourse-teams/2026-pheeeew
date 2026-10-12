package com.pheeeew.emotion.domain.repository.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record EmotionSearchFilter(
        LocalDate startDate,
        LocalDate endDate,
        EmotionContentFilter contentType,
        EmotionSearchScope scope,
        UUID groupId
) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public EmotionSearchFilter {
        if (!isDateRangeValid(startDate, endDate)) {
            throw new IllegalArgumentException("0001~9999년의 시작일과 종료일을 함께 전달하고 시작일은 종료일 이하여야 합니다.");
        }
        if (contentType == null) {
            contentType = EmotionContentFilter.ALL;
        }
        if (scope == null) {
            scope = groupId == null ? EmotionSearchScope.ALL : EmotionSearchScope.GROUP;
        }
        if ((scope == EmotionSearchScope.GROUP) != (groupId != null)) {
            throw new IllegalArgumentException("GROUP 범위에서만 그룹 ID를 필수로 전달해야 합니다.");
        }
    }

    public static EmotionSearchFilter of(
            LocalDate startDate,
            LocalDate endDate,
            EmotionContentFilter contentType,
            EmotionSearchScope scope,
            UUID groupId
    ) {
        return new EmotionSearchFilter(startDate, endDate, contentType, scope, groupId);
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
