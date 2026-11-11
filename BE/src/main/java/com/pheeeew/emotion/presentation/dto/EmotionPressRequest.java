package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

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
        Map<EmotionState, Integer> counts,

        @Schema(
                description = "응답을 받지 못한 묶음을 다시 보낼 때 재사용하는 요청 식별자. 선택 값입니다. "
                        + "지금은 서버에 따라 이 값을 무시할 수 있어 중복 반영 방지를 보장하지 않습니다. "
                        + "같은 requestId 는 같은 counts 로만 다시 보냅니다. 상한을 적용한 뒤 반영되는 횟수가 같으면 같은 요청으로 봅니다. "
                        + "409 를 받으면 그 requestId 로 다시 보내지 말고 새 requestId 로 새 묶음을 보냅니다.",
                example = "6f1c2b8e-3a4d-4c5e-9f60-7a8b9c0d1e2f"
        )
        UUID requestId
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
