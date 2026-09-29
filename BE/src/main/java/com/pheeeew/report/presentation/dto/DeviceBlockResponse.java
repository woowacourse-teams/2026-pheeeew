package com.pheeeew.report.presentation.dto;

import com.pheeeew.report.application.dto.BlockResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DeviceBlockResponse(
        @Schema(
                description = """
                        사용자 차단 ID입니다. 해제할 때 `DELETE /api/v2/blocks/devices/{blockId}` 의 경로 변수로 사용합니다.

                        감정 ID인 `emotionId`와 다른 값입니다.
                        """,
                example = "7"
        )
        Long blockId,

        @Schema(
                description = """
                        차단의 근거가 된 감정 ID입니다.

                        이미 차단한 사용자를 다른 감정으로 다시 차단하면 요청에 담은 감정이 아니라 최초 차단의 근거 감정을 반환합니다.
                        """,
                example = "42"
        )
        Long emotionId,

        @Schema(
                description = "근거가 된 감정의 익명 닉네임입니다. 차단한 사용자를 식별하는 값이 아닙니다.",
                example = "날아가는 고라니"
        )
        String nickname,

        @Schema(
                description = "근거가 된 감정의 메모입니다. 메모가 없는 감정이면 `null`입니다.",
                nullable = true,
                example = "오늘은 조금 지쳤다"
        )
        String memo,

        @Schema(
                description = "차단한 시각입니다. 감정을 등록한 시각이 아닙니다.",
                example = "2026-09-14T02:44:00Z"
        )
        Instant createdAt
) {

    public static DeviceBlockResponse from(BlockResult result) {
        return new DeviceBlockResponse(
                result.blockId(),
                result.emotionId(),
                result.nickname(),
                result.memo(),
                result.createdAt()
        );
    }
}
