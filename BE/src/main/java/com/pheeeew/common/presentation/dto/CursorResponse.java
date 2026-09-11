package com.pheeeew.common.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CursorResponse<T>(
        @Schema(description = "현재 페이지 항목")
        List<T> items,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 조회에 사용할 서버 발급 커서", nullable = true, example = "opaque-cursor")
        String nextCursor
) {

    public static <T> CursorResponse<T> of(List<T> items, boolean hasNext, String nextCursor) {
        return new CursorResponse<>(items, hasNext, nextCursor);
    }
}
