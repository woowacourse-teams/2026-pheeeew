package com.pheeeew.pressevent.presentation.dto;

import com.pheeeew.pressevent.application.PressEventAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record PressEventAttemptResponse(
        @Schema(description = "이 도전의 requestId")
        UUID attemptId,
        @Schema(description = "서버가 계산한 이번 도전의 프레스 합계")
        int total,
        int bestScore,
        long rank,
        @Schema(description = "최초 결과 반영 직후 순위를 계산한 시각")
        Instant rankedAt,
        @Schema(description = "이전 성공 응답을 재전달하면 true")
        boolean replayed
) {

    public static PressEventAttemptResponse from(PressEventAttemptResult result) {
        return new PressEventAttemptResponse(result.attemptId(), result.total(), result.bestScore(), result.rank(),
                result.rankedAt(), result.replayed());
    }
}
