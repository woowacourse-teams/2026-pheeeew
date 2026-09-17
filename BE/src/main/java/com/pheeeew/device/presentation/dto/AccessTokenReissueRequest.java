package com.pheeeew.device.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AccessTokenReissueRequest(
        @NotBlank(message = "refresh token은 필수입니다.")
        @Schema(
                description = "기기 등록 때 받은 `refreshToken`을 그대로 보냅니다.",
                example = "550e8400-e29b-41d4-a716-446655440000.dBjftJeZ4CVP..."
        )
        String refreshToken
) {
}
