package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record EmotionPressTotalResponse(
        @Schema(
                description = "서버가 daysAgo 를 해석한 날짜. 하루 경계는 KST 00:00 입니다.",
                example = "2026-10-08"
        )
        LocalDate pressDate,
        @Schema(description = "그날 전체 사용자가 누른 횟수의 합. 감정별로 나누지 않습니다.", example = "48213")
        long total
) {
    public static EmotionPressTotalResponse from(EmotionPressTotalResult result) {
        return new EmotionPressTotalResponse(result.pressDate(), result.total());
    }
}
