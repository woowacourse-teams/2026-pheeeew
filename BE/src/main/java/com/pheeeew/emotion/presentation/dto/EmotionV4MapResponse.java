package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "EmotionV4MapResponse")
public record EmotionV4MapResponse(
        @Schema(example = "Feature") String type,
        @Schema(description = "지도에 표시할 감정 ID") Long id,
        PointGeometry geometry,
        Properties properties
) {

    public static EmotionV4MapResponse from(EmotionMapItemView view) {
        return new EmotionV4MapResponse("Feature", view.id(), PointGeometry.of(view.longitude(), view.latitude()),
                Properties.from(view));
    }

    @Schema(name = "EmotionV4MapProperties")
    public record Properties(
            @Schema(description = "감정 작성 시각. 오래된 감정부터 그리면 최신 감정이 위에 표시됩니다.") Instant createdAt,
            EmotionState state,
            @Schema(description = "스탬프 회전 각도. 0도 이상 360도 미만") double rotationDegrees
    ) {

        public static Properties from(EmotionMapItemView view) {
            return new Properties(view.createdAt(), view.state(), view.rotationDegrees());
        }
    }
}
