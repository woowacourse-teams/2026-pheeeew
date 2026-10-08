package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record EmotionPressResponse(
        @Schema(
                description = "서버가 요청 좌표로 결정한 읍면동 코드 8자리. 클라이언트가 지정할 수 없습니다.",
                example = "11010530"
        )
        String regionCode,

        @Schema(
                description = "이 기기가 해당 읍면동에서 오늘 누른 감정별 횟수. "
                        + "누르지 않은 감정도 0으로 내려와 다섯 감정이 항상 모두 있습니다. 지역 전체 합이 아닙니다.",
                example = """
                        {"FRUSTRATED": 0, "IRRITATED": 0, "EXHAUSTED": 3, "DISCOURAGED": 0, "ANGRY": 9}
                        """
        )
        Map<EmotionState, Long> counts,

        @Schema(description = "counts 값의 합")
        long total
) {

    public static EmotionPressResponse from(EmotionPressResult result) {
        return new EmotionPressResponse(result.regionCode(), result.counts(), result.total());
    }
}
