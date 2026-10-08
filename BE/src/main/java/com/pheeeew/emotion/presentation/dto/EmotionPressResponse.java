package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record EmotionPressResponse(
        @Schema(
                description = "이 기기가 오늘 누른 감정별 횟수. "
                        + "누르지 않은 감정도 0으로 내려와 다섯 감정이 항상 모두 있습니다. "
                        + "GET /api/v2/emotions/presses/me 의 오늘 값과 같습니다.",
                example = """
                        {"FRUSTRATED": 0, "IRRITATED": 0, "EXHAUSTED": 3, "DISCOURAGED": 0, "ANGRY": 9}
                        """
        )
        Map<EmotionState, Long> counts,

        @Schema(description = "counts 값의 합")
        long total
) {

    public static EmotionPressResponse from(EmotionPressResult result) {
        return new EmotionPressResponse(result.counts(), result.total());
    }
}
