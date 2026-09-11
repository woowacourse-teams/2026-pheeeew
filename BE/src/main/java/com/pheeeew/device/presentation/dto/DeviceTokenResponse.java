package com.pheeeew.device.presentation.dto;

import com.pheeeew.device.application.dto.DeviceSaveResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceTokenResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiJ9...")
        String accessToken,

        @Schema(
                description = """
                        앱이 보관하는 장기 비밀값입니다.

                        만료가 없으며 재발급 시에도 새로 발급되지 않습니다. 안전하게 보관해야 합니다.
                        """,
                example = "550e8400-e29b-41d4-a716-446655440000.dBjftJeZ4CVP..."
        )
        String refreshToken,

        @Schema(description = "access token의 유효 시간(초)입니다. 서버가 정합니다.", example = "1800")
        long expiresIn
) {

    public static DeviceTokenResponse from(DeviceSaveResult result) {
        return new DeviceTokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn()
        );
    }
}
