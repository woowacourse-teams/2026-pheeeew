package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(name = "EmotionV4MapRequest")
public record EmotionV4MapRequest(
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        @Schema(description = "첫 페이지 검색 영역의 서쪽 경계 경도", minimum = "-180", maximum = "180", example = "126.9")
        Double minLongitude,

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        @Schema(description = "첫 페이지 검색 영역의 최소 위도", minimum = "-90", maximum = "90", example = "37.5")
        Double minLatitude,

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        @Schema(description = "첫 페이지 검색 영역의 동쪽 경계 경도", minimum = "-180", maximum = "180", example = "127.1")
        Double maxLongitude,

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        @Schema(description = "첫 페이지 검색 영역의 최대 위도", minimum = "-90", maximum = "90", example = "37.6")
        Double maxLatitude,

        @Schema(description = "다음 페이지 조회에 사용할 서버 발급 커서", example = "opaque-cursor")
        String cursor,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 시작일(포함). 종료일과 함께 지정하며 둘 다 생략하면 전체 기간입니다.", example = "2026-10-01")
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 종료일(포함). 시작일 이상이어야 합니다.", example = "2026-10-11")
        LocalDate endDate,

        @Schema(description = "내용 필터. 첫 페이지에서 생략하면 ALL이며 NONE은 항상 제외합니다.")
        EmotionContentFilter contentType,

        @Schema(description = "조회 범위. ALL·MY만 허용하며 첫 페이지에서 생략하면 ALL입니다. 다음 페이지에서는 생략 시 커서의 조건을 유지합니다.", allowableValues = {"ALL", "MY"})
        EmotionSearchScope scope
) {

    @AssertTrue
    @Schema(hidden = true)
    public boolean isPageRequestValid() {
        boolean allBoundsPresent = minLongitude != null
                && minLatitude != null
                && maxLongitude != null
                && maxLatitude != null;
        boolean allBoundsAbsent = minLongitude == null
                && minLatitude == null
                && maxLongitude == null
                && maxLatitude == null;

        return cursor == null ? allBoundsPresent : allBoundsAbsent;
    }

    @AssertTrue
    @Schema(hidden = true)
    public boolean isLongitudeRangeValid() {
        return minLongitude == null
                || maxLongitude == null
                || minLongitude < maxLongitude
                || minLongitude > maxLongitude;
    }

    @AssertTrue
    @Schema(hidden = true)
    public boolean isLatitudeRangeValid() {
        return minLatitude == null || maxLatitude == null || minLatitude < maxLatitude;
    }

    @AssertTrue(message = "0001~9999년의 시작일과 종료일을 함께 전달하고, 시작일은 종료일 이하여야 합니다.")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return EmotionSearchFilter.isDateRangeValid(startDate, endDate);
    }

    @AssertTrue(message = "v4는 ALL·MY 범위만 허용합니다.")
    @Schema(hidden = true)
    public boolean isScopeValid() {
        return scope != EmotionSearchScope.GROUP;
    }

    public EmotionSearchBounds toBounds() {
        if (cursor != null) {
            return null;
        }

        return EmotionSearchBounds.of(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }
}
