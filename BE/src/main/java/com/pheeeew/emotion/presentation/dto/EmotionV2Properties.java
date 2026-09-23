package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionResult;
import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SighV2Properties")
public record EmotionV2Properties(
        @Schema(description = "한숨 생성 시각", example = "2026-09-01T12:00:00Z")
        Instant createdAt,

        @Schema(description = "정규화되어 저장된 선택 메모", nullable = true, example = "오늘은 조금 지쳤다")
        String memo,

        @Schema(description = "서버에서 최초 등록 시 생성한 익명 닉네임", example = "날아가는 고라니")
        String nickname,

        @Schema(description = "인증된 기기의 좋아요 여부", example = "true")
        boolean liked,

        @Schema(description = "한숨의 전체 좋아요 수", minimum = "0", example = "12")
        long likeCount
) {

    public static EmotionV2Properties of(EmotionResult emotion, EmotionLikeResult like) {
        return new EmotionV2Properties(
                emotion.createdAt(), emotion.memo(), emotion.nickname(), like.liked(), like.likeCount()
        );
    }
}
