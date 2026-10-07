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
                description = "최초 차단 근거 감정이 익명이면 '익명', 기명이면 작성 기기의 현재 닉네임입니다. 다른 감정으로 재시도해도 최초 근거의 익명 선택을 유지합니다. 기기 닉네임이 없으면 '익명'으로 표시합니다.",
                example = "익명"
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
