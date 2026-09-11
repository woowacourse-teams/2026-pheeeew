package com.pheeeew.device.presentation.dto;

import com.pheeeew.device.domain.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DeviceAttestationRequest(
        @NotNull(message = "플랫폼은 필수입니다.")
        @Schema(
                description = "기기 플랫폼입니다. `ANDROID` 또는 `IOS`만 보낼 수 있습니다.",
                example = "ANDROID"
        )
        DevicePlatform platform,

        @Schema(
                description = """
                        무결성 증명 토큰입니다.

                        현재 버전에서는 검증하지 않으며 저장하지도 않습니다. 보낼 값이 없으면 `null`로 두거나 생략합니다.
                        검증이 추가되면 필수가 됩니다.
                        """,
                nullable = true
        )
        String token,

        @Schema(
                description = """
                        무결성 증명 키 식별자입니다.

                        현재 버전에서는 검증하지 않으며 저장하지도 않습니다. 보낼 값이 없으면 `null`로 두거나 생략합니다.
                        검증이 추가되면 필수가 됩니다.
                        """,
                nullable = true
        )
        String keyId
) {
}
