package com.pheeeew.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CollectionEmotionRequest(
        @Size(max = 2048)
        @Schema(description = "같은 카드 감정 목록의 다음 페이지 커서. 첫 페이지·새로고침에서는 생략합니다.")
        String cursor
) {
}
