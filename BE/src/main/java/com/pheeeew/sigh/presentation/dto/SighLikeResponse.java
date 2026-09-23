package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.like.dto.EmotionLikeResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SighLikeResponse(
        @Schema(description = "처리 후 인증된 기기의 좋아요 여부", example = "true")
        boolean liked,

        @Schema(description = "이번 요청 처리 결과의 전체 좋아요 수", minimum = "0", example = "12")
        long likeCount
) {

    public static SighLikeResponse from(EmotionLikeResult result) {
        return new SighLikeResponse(result.liked(), result.likeCount());
    }
}
