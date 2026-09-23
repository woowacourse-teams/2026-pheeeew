package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SighLikeRequest(
        @NotNull(message = "좋아요 상태는 필수입니다.")
        @Schema(description = "원하는 좋아요 상태. true는 추가, false는 취소이며 생략과 null은 허용하지 않습니다.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean liked
) {
}
