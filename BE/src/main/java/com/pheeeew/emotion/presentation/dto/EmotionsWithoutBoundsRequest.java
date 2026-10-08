package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "EmotionsWithoutBoundsRequest")
public record EmotionsWithoutBoundsRequest(
        @Schema(description = "다음 페이지 조회에 사용할 서버 발급 커서. 첫 페이지에서는 생략합니다.", example = "opaque-cursor")
        String cursor,

        @Schema(description = "첫 페이지의 그룹 공개 ID. 첫 페이지에서 생략하면 그룹 없는 감정까지 전체 조회합니다. "
                + "다음 페이지에서는 생략하거나 커서에 저장된 동일한 그룹 ID를 전달할 수 있습니다. 그룹 변경 시 커서를 생략합니다.")
        UUID groupId
) {
}
