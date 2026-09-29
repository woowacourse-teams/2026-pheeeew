package com.pheeeew.groups.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import jakarta.validation.constraints.NotNull;

public record GroupPressRequest(
        @NotNull(message = "감정 상태는 필수입니다.")
        EmotionState state
) {
}
