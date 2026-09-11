package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.SighSearchBounds;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SighMapRequest(
        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        @Schema(description = "지도 화면의 서쪽 경계 경도", minimum = "-180", maximum = "180", example = "127.10")
        Double minLongitude,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        @Schema(description = "지도 화면의 최소 위도", minimum = "-90", maximum = "90", example = "37.30")
        Double minLatitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        @Schema(description = "지도 화면의 동쪽 경계 경도", minimum = "-180", maximum = "180", example = "127.20")
        Double maxLongitude,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        @Schema(description = "지도 화면의 최대 위도", minimum = "-90", maximum = "90", example = "37.40")
        Double maxLatitude
) {

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

    public SighSearchBounds toBounds() {
        return SighSearchBounds.of(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }
}
