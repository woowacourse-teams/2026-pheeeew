package com.pheeeew.collection.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CollectionClaimRequest(
        @NotNull
        @Schema(description = "컬렉션 화면에서 확인한 한국 시간 기준 날짜. 미발급 카드는 당일에만 수령할 수 있습니다.",
                example = "2026-10-22") LocalDate date,
        @NotNull EmotionState state
) {
}
