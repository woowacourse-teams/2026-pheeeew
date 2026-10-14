package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.RegionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

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

        @Schema(description = "GROUP 범위의 그룹 공개 ID. scope 없이 지정하면 GROUP으로 조회합니다.") UUID groupId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 시작일(포함). 종료일과 함께 지정하며 둘 다 생략하면 전체 기간입니다.", example = "2026-10-01")
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Schema(description = "KST 기준 종료일(포함). 시작일 이상이어야 합니다.", example = "2026-10-13")
        LocalDate endDate,

        @Schema(description = "내용 필터. 생략하면 ALL이며 NONE은 항상 제외합니다.") EmotionContentFilter contentType,

        @Schema(description = "조회 범위. 생략하면 ALL(groupId가 있으면 GROUP). MY는 인증된 기기 기준입니다.") EmotionSearchScope scope
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

    @AssertTrue(message = "0001~9999년의 시작일과 종료일을 함께 전달하고, 시작일은 종료일 이하여야 합니다.")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return EmotionSearchFilter.isDateRangeValid(startDate, endDate);
    }

    @AssertTrue(message = "GROUP에는 그룹 ID가 필요하며 ALL·MY에는 그룹 ID를 전달할 수 없습니다.")
    @Schema(hidden = true)
    public boolean isScopeValid() {
        return scope == null || (scope == EmotionSearchScope.GROUP) == (groupId != null);
    }

    public EmotionSearchFilter toFilter() {
        return EmotionSearchFilter.of(startDate, endDate, contentType, scope, groupId);
    }

    public EmotionSearchBounds toBounds() {
        return EmotionSearchBounds.of(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }
}
