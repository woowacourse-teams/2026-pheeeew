package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.SighResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SighV2Properties(
        @Schema(description = "한숨 생성 시각", example = "2026-09-01T12:00:00Z")
        Instant createdAt,

        @Schema(description = "정규화되어 저장된 선택 메모", nullable = true, example = "오늘은 조금 지쳤다")
        String memo,

        @Schema(description = "서버에서 최초 등록 시 생성한 익명 닉네임", example = "날아가는 고라니")
        String nickname
) {

    public static SighV2Properties from(SighResult sigh) {
        return new SighV2Properties(sigh.createdAt(), sigh.memo(), sigh.nickname());
    }
}
