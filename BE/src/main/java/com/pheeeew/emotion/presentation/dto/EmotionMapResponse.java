package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.presentation.dto.GroupStampResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "EmotionMapResponse")
public record EmotionMapResponse(
        @Schema(example = "Feature") String type,
        @Schema(description = "지도에 표시할 감정 ID") Long id,
        PointGeometry geometry,
        Properties properties
) {

    public static EmotionMapResponse from(EmotionMapItemView view) {
        return new EmotionMapResponse("Feature", view.id(), PointGeometry.of(view.longitude(), view.latitude()),
                new Properties(view.createdAt(), view.state(), view.rotationDegrees(),
                        view.groupStamp() == null ? null : GroupStampResponse.from(view.groupStamp()), view.groupId()));
    }

    public record Properties(
            @Schema(description = "감정 작성 시각. 오래된 감정부터 그리면 최신 감정이 위에 표시됩니다.") Instant createdAt,
            EmotionState state,
            @Schema(description = "스탬프 회전 각도. 0도 이상 360도 미만") double rotationDegrees,
            @Schema(description = "현재 그룹 스탬프 모양. 선택하지 않았으면 null입니다.", nullable = true) GroupStampResponse groupStamp,
            @Schema(description = "선택한 그룹의 공개 ID. 그룹 스탬프가 없으면 null입니다.", nullable = true) UUID groupId
    ) {
    }
}
