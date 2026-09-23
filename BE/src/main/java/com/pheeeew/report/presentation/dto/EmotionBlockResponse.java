package com.pheeeew.report.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pheeeew.report.application.dto.BlockResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SighBlockResponse")
public record EmotionBlockResponse(
        @Schema(
                description = "차단한 한숨 ID입니다. 해제할 때 `DELETE /api/v2/blocks/sighs/{sighId}` 의 경로 변수로 사용합니다.",
                example = "42"
        )
        @JsonProperty("sighId")
        Long emotionId,

        @Schema(description = "차단한 한숨의 익명 닉네임", example = "날아가는 고라니")
        String nickname,

        @Schema(
                description = "차단한 한숨의 메모입니다. 메모가 없는 한숨이면 `null`입니다.",
                nullable = true,
                example = "오늘은 조금 지쳤다"
        )
        String memo,

        @Schema(
                description = "차단한 시각입니다. 한숨을 등록한 시각이 아닙니다.",
                example = "2026-09-14T02:44:00Z"
        )
        Instant createdAt
) {

    public static EmotionBlockResponse from(BlockResult result) {
        return new EmotionBlockResponse(
                result.emotionId(),
                result.nickname(),
                result.memo(),
                result.createdAt()
        );
    }
}
