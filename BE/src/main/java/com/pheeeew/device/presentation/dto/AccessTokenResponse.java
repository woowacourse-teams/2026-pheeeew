package com.pheeeew.device.presentation.dto;

import com.pheeeew.device.application.dto.AccessTokenResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccessTokenResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiJ9...")
        String accessToken,

        @Schema(description = "access token의 유효 시간(초)입니다. 서버가 정합니다.", example = "1800")
        long expiresIn
) {

    public static AccessTokenResponse from(AccessTokenResult result) {
        return new AccessTokenResponse(result.accessToken(), result.expiresIn());
    }
}
