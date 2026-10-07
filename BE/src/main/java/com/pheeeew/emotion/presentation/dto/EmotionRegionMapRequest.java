package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.region.domain.RegionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "EmotionRegionMapRequest")
public record EmotionRegionMapRequest(
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        @Schema(description = "화면의 서쪽 경계 경도. 날짜변경선을 넘으면 maxLongitude보다 큽니다.",
                minimum = "-180", maximum = "180", example = "126.9") Double minLongitude,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        @Schema(description = "화면의 최소 위도. maxLatitude보다 작아야 합니다.",
                minimum = "-90", maximum = "90", example = "37.5") Double minLatitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        @Schema(description = "화면의 동쪽 경계 경도. minLongitude와 달라야 합니다.",
                minimum = "-180", maximum = "180", example = "127.1") Double maxLongitude,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        @Schema(description = "화면의 최대 위도", minimum = "-90", maximum = "90", example = "37.6") Double maxLatitude,

        @NotNull
        @Schema(description = "클라이언트가 확대 정도에 따라 선택한 지역 계층. 서버는 다른 계층으로 전환하지 않습니다.") RegionLevel level,

        @Schema(description = "조회할 그룹의 공개 ID. 생략하면 그룹 없는 감정까지 전체 조회합니다.") UUID groupId
) {

    @AssertTrue
    @Schema(hidden = true)
    public boolean isLongitudeRangeValid() {
        return minLongitude == null || maxLongitude == null || minLongitude < maxLongitude || minLongitude > maxLongitude;
    }

    @AssertTrue
    @Schema(hidden = true)
    public boolean isLatitudeRangeValid() {
        return minLatitude == null || maxLatitude == null || minLatitude < maxLatitude;
    }

    public EmotionSearchBounds toBounds() {
        return EmotionSearchBounds.of(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }
}
