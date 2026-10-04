package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.region.domain.RegionLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmotionRegionMapResponse")
public record EmotionRegionMapResponse(
        @Schema(example = "Feature") String type,
        @Schema(description = "SGIS 지역 코드 문자열. 시도 2자리, 시군구 5자리, 읍면동 8자리입니다.") String id,
        @Schema(description = "지역 내부의 고정 표시점. 요청 화면 밖에 있을 수 있습니다.") PointGeometry geometry,
        Properties properties
) {

    public static EmotionRegionMapResponse from(EmotionRegionMapItemView view) {
        return new EmotionRegionMapResponse("Feature", view.region().code(),
                PointGeometry.of(view.region().longitude(), view.region().latitude()), Properties.from(view));
    }

    public record Properties(
            RegionLevel level,
            String name,
            @Schema(description = "상위 지역의 SGIS 코드. 시도이면 null입니다.", nullable = true) String parentCode,
            @Schema(description = "화면이 아닌 지역 전체의 기간 하한 없는 누적 감정 개수", minimum = "1") long totalCount,
            @Schema(description = "최빈 감정. 동률은 ANGRY, DISCOURAGED, EXHAUSTED, FRUSTRATED, IRRITATED 순서입니다."
                    + " 모든 대상의 상태가 null이면 null입니다.", nullable = true) EmotionState representativeState
    ) {

        public static Properties from(EmotionRegionMapItemView view) {
            return new Properties(view.region().level(), view.region().name(), view.region().parentCode(),
                    view.summary().totalCount(), view.summary().representativeState());
        }
    }
}
