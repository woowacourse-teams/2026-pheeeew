package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

public record EmotionPressDailyResponse(
        @Schema(
                description = "서버가 daysAgo 를 해석한 날짜. 하루 경계는 KST 00:00 입니다.",
                example = "2026-10-08"
        )
        LocalDate pressDate,
        @Schema(
                description = "이 기기가 그날 누른 감정별 횟수. 지역을 구분하지 않고 모두 합칩니다. "
                        + "누르지 않은 감정도 0으로 내려와 다섯 감정이 항상 모두 있습니다.",
                example = """
                        {"FRUSTRATED": 12, "IRRITATED": 3, "EXHAUSTED": 27, "DISCOURAGED": 0, "ANGRY": 8}
                        """
        )
        Map<EmotionState, Long> counts,
        @Schema(description = "counts 값의 합")
        long total
) {
    public static EmotionPressDailyResponse from(EmotionPressDailyResult result) {
        return new EmotionPressDailyResponse(result.pressDate(), result.counts(), result.total());
    }
}
