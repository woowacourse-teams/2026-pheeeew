package com.pheeeew.collection.presentation.dto;

import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CollectionHistoryRequest(
        @Schema(description = "감정 필터. 첫 페이지에서 생략하면 전체 감정이며 다음 페이지에서 생략하면 커서의 필터를 유지합니다.")
        EmotionState emotion,
        @Min(1) @Max(50)
        @Schema(description = "페이지 크기. 다음 페이지에서도 원하는 크기를 전달합니다.", minimum = "1", maximum = "50", defaultValue = "20")
        Integer limit,
        @Size(max = 512)
        @Schema(description = "서버가 반환한 다음 페이지 커서. 첫 페이지와 새로고침에서는 생략합니다.")
        String cursor
) {
}
