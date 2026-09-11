package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.SighSearchBounds;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record SighListRequest(
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
        String cursor
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

    public boolean isNextPageRequest() {
        return cursor != null;
    }

    public SighSearchBounds toBounds() {
        return SighSearchBounds.of(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }
}
