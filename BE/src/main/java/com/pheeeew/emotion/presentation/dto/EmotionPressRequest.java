package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EmotionPressRequest(
        @NotNull
        @Schema(
                description = "감정별 누른 횟수. "
                        + "키는 FRUSTRATED, IRRITATED, EXHAUSTED, DISCOURAGED, ANGRY 중 하나이고 값은 0 이상의 정수입니다. "
                        + "값이 0 인 감정과 빈 객체는 누르지 않은 것으로 보고 넘기며 오늘 집계만 돌려줍니다. "
                        + "값이 음수이거나 null 이면 400 입니다. "
                        + "감정 하나당 30, 요청 전체 합 100 을 넘으면 거절하지 않고 넘친 만큼 버립니다.",
                example = """
                        {"ANGRY": 9, "EXHAUSTED": 3}
                        """
        )
        Map<EmotionState, Integer> counts
) {

    @AssertTrue(message = "감정별 횟수는 null 이 아닌 0 이상인 값만 담아야 합니다.")
    @Schema(hidden = true)
    public boolean isCountsValid() {
        if (counts == null) {
            return true;
        }

        return counts.values().stream()
                .allMatch(count -> count != null && count >= 0);
    }
}
